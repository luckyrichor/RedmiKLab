#include "tcp_proxy_session.h"

#include "tcp_packet_builder.h"

#include <algorithm>
#include <array>

namespace redmiklab {
namespace {
constexpr uint8_t kFin = 0x01;
constexpr uint8_t kSyn = 0x02;
constexpr uint8_t kRst = 0x04;
constexpr uint8_t kAck = 0x10;
constexpr uint8_t kPshAck = 0x18;
constexpr size_t kReadBufferBytes = 16 * 1024;
constexpr uint64_t kRetransmitAfterMillis = 1000;
constexpr uint8_t kMaximumRetransmissions = 5;

bool sequence_at_or_after(uint32_t sequence, uint32_t expected) {
    return static_cast<int32_t>(sequence - expected) >= 0;
}
}

TcpProxySession::TcpProxySession(
        std::unique_ptr<TcpStream> stream,
        uint32_t initial_server_sequence,
        SessionObserver observer)
    : stream_(std::move(stream)),
      observer_(std::move(observer)),
      initial_server_sequence_(initial_server_sequence),
      server_next_sequence_(initial_server_sequence + 1) {}

std::vector<std::vector<uint8_t>> TcpProxySession::on_client_packet(const std::vector<uint8_t>& packet) {
    std::vector<std::vector<uint8_t>> responses;
    if (closed_ || stream_ == nullptr) return responses;
    const auto metadata = parse_ip_packet(packet.data(), packet.size());
    if (!metadata.has_value() || metadata->protocol != TransportProtocol::Tcp) return responses;
    packet_template_ = *metadata;

    if ((metadata->tcp_flags & kRst) != 0) {
        closed_ = true;
        return responses;
    }
    if ((metadata->tcp_flags & kSyn) != 0 && connected_ && metadata->tcp_sequence == client_initial_sequence_) {
        responses.push_back(build_tcp_response(
            *metadata, initial_server_sequence_, client_initial_sequence_ + 1, kSyn | kAck, nullptr, 0));
        return responses;
    }
    if ((metadata->tcp_flags & kSyn) != 0 && !connecting_ && !connected_) {
        client_initial_sequence_ = metadata->tcp_sequence;
        client_next_sequence_ = metadata->tcp_sequence + 1;
        const auto result = stream_->connect_to(destination_ip_address(*metadata), metadata->destination_port);
        if (result == SocketConnectResult::Failed) {
            if (observer_) observer_(ForwardingDirection::Upstream, ForwardingStage::Dropped,
                ForwardingOutcome::Failed, metadata->payload_bytes, "CONNECT_FAILED");
            responses.push_back(build_tcp_reset(*metadata));
            closed_ = true;
        } else if (result == SocketConnectResult::Connected) {
            connected_ = true;
            responses.push_back(build_tcp_response(
                *metadata, initial_server_sequence_, client_next_sequence_, kSyn | kAck, nullptr, 0));
        } else {
            connecting_ = true;
        }
        return responses;
    }
    if (!connected_) return responses;

    if ((metadata->tcp_flags & kAck) != 0 && !pending_server_packet_.empty() &&
        sequence_at_or_after(metadata->tcp_acknowledgement, pending_server_end_sequence_)) {
        pending_server_packet_.clear();
        pending_server_retransmissions_ = 0;
        if (remote_fin_sent_ && sequence_at_or_after(metadata->tcp_acknowledgement, server_next_sequence_)) {
            remote_fin_acknowledged_ = true;
            if (client_fin_received_) closed_ = true;
        }
    }

    if (metadata->payload_bytes > 0) {
        const uint32_t payload_end = metadata->tcp_sequence + metadata->payload_bytes;
        if (metadata->tcp_sequence <= client_next_sequence_ && payload_end > client_next_sequence_) {
            const size_t already_sent = client_next_sequence_ - metadata->tcp_sequence;
            const size_t pending_bytes = metadata->payload_bytes - already_sent;
            const auto sent = stream_->send_bytes(
                packet.data() + metadata->payload_offset + already_sent,
                pending_bytes);
            if (sent > 0) {
                client_next_sequence_ += static_cast<uint32_t>(sent);
                if (observer_) observer_(ForwardingDirection::Upstream,
                    ForwardingStage::UpstreamSocketAccepted, ForwardingOutcome::Accepted,
                    static_cast<uint64_t>(sent), "");
            } else if (sent < 0 && observer_) {
                observer_(ForwardingDirection::Upstream, ForwardingStage::Dropped,
                    ForwardingOutcome::Failed, pending_bytes, "WRITE_FAILED");
            }
        }
        responses.push_back(build_tcp_response(
            *metadata, server_next_sequence_, client_next_sequence_, kAck, nullptr, 0));
    }
    if ((metadata->tcp_flags & kFin) != 0 && metadata->tcp_sequence + metadata->payload_bytes == client_next_sequence_) {
        ++client_next_sequence_;
        client_fin_received_ = true;
        stream_->shutdown_write();
        responses.push_back(build_tcp_response(
            *metadata, server_next_sequence_, client_next_sequence_, kAck, nullptr, 0));
        if (remote_fin_acknowledged_) closed_ = true;
    }
    return responses;
}

std::vector<std::vector<uint8_t>> TcpProxySession::poll(uint64_t now_millis) {
    if (closed_ || stream_ == nullptr) return {};
    if (connecting_) return complete_connect();
    if (!connected_ || !packet_template_.has_value()) return {};
    if (!pending_server_packet_.empty()) {
        if (now_millis > 0 && now_millis - pending_server_sent_at_millis_ >= kRetransmitAfterMillis) {
            if (pending_server_retransmissions_ >= kMaximumRetransmissions) {
                if (observer_) observer_(ForwardingDirection::Downstream, ForwardingStage::Dropped,
                    ForwardingOutcome::Failed, pending_server_packet_.size(), "TUN_ACK_TIMEOUT");
                closed_ = true;
                return {build_tcp_response(
                    *packet_template_, server_next_sequence_, client_next_sequence_, 0x14, nullptr, 0)};
            }
            ++pending_server_retransmissions_;
            pending_server_sent_at_millis_ = now_millis;
            return {pending_server_packet_};
        }
        return {};
    }

    std::array<uint8_t, kReadBufferBytes> buffer{};
    const auto received = stream_->receive_bytes(buffer.data(), buffer.size());
    if (received < 0) return {};
    if (received == 0) {
        if (remote_fin_sent_) return {};
        auto packet = build_tcp_response(
            *packet_template_, server_next_sequence_, client_next_sequence_, kFin | kAck, nullptr, 0);
        ++server_next_sequence_;
        remote_fin_sent_ = true;
        pending_server_packet_ = packet;
        pending_server_end_sequence_ = server_next_sequence_;
        pending_server_sent_at_millis_ = now_millis;
        pending_server_retransmissions_ = 0;
        return {std::move(packet)};
    }
    auto packet = build_tcp_response(
        *packet_template_,
        server_next_sequence_,
        client_next_sequence_,
        kPshAck,
        buffer.data(),
        static_cast<size_t>(received));
    if (observer_) observer_(ForwardingDirection::Downstream,
        ForwardingStage::DownstreamSocketReceived, ForwardingOutcome::Received,
        static_cast<uint64_t>(received), "");
    server_next_sequence_ += static_cast<uint32_t>(received);
    pending_server_packet_ = packet;
    pending_server_end_sequence_ = server_next_sequence_;
    pending_server_sent_at_millis_ = now_millis;
    pending_server_retransmissions_ = 0;
    return {std::move(packet)};
}

std::vector<std::vector<uint8_t>> TcpProxySession::complete_connect() {
    const auto result = stream_->finish_connect();
    if (result == SocketConnectResult::Connecting) return {};
    connecting_ = false;
    if (!packet_template_.has_value()) {
        closed_ = true;
        return {};
    }
    if (result == SocketConnectResult::Failed) {
        if (observer_) observer_(ForwardingDirection::Upstream, ForwardingStage::Dropped,
            ForwardingOutcome::Failed, 0, "CONNECT_FAILED");
        closed_ = true;
        return {build_tcp_reset(*packet_template_)};
    }
    connected_ = true;
    return {build_tcp_response(
        *packet_template_, initial_server_sequence_, client_next_sequence_, kSyn | kAck, nullptr, 0)};
}

}  // namespace redmiklab
