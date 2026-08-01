#include "udp_proxy_session.h"

#include "udp_packet_builder.h"

#include <array>

namespace redmiklab {
namespace {
constexpr size_t kMaximumUdpPayload = 65507;
}

UdpProxySession::UdpProxySession(std::unique_ptr<UdpStream> stream, SessionObserver observer)
    : stream_(std::move(stream)), observer_(std::move(observer)) {}

bool UdpProxySession::on_client_packet(const std::vector<uint8_t>& packet) {
    if (failed_ || stream_ == nullptr) return false;
    const auto metadata = parse_ip_packet(packet.data(), packet.size());
    if (!metadata.has_value() || metadata->protocol != TransportProtocol::Udp) return false;
    if (!connected_) {
        if (!stream_->connect_to(destination_ip_address(*metadata), metadata->destination_port)) {
            failed_ = true;
            if (observer_) observer_(ForwardingDirection::Upstream, ForwardingStage::Dropped,
                ForwardingOutcome::Failed, metadata->payload_bytes, "CONNECT_FAILED");
            return false;
        }
        connected_ = true;
    }
    packet_template_ = *metadata;
    const auto sent = stream_->send_datagram(packet.data() + metadata->payload_offset, metadata->payload_bytes);
    if (sent > 0 && observer_) observer_(ForwardingDirection::Upstream,
        ForwardingStage::UpstreamSocketAccepted, ForwardingOutcome::Accepted,
        static_cast<uint64_t>(sent), "");
    if (sent < 0 && observer_) observer_(ForwardingDirection::Upstream,
        ForwardingStage::Dropped, ForwardingOutcome::Failed,
        metadata->payload_bytes, "WRITE_FAILED");
    if (sent >= 0 && static_cast<size_t>(sent) < metadata->payload_bytes && observer_) {
        observer_(ForwardingDirection::Upstream, ForwardingStage::Dropped,
            ForwardingOutcome::Failed, metadata->payload_bytes - static_cast<size_t>(sent),
            "PARTIAL_WRITE");
    }
    return sent == metadata->payload_bytes;
}

std::vector<std::vector<uint8_t>> UdpProxySession::poll() {
    if (!connected_ || failed_ || !packet_template_.has_value()) return {};
    std::array<uint8_t, kMaximumUdpPayload> buffer{};
    const auto received = stream_->receive_datagram(buffer.data(), buffer.size());
    if (received <= 0) return {};
    if (observer_) observer_(ForwardingDirection::Downstream,
        ForwardingStage::DownstreamSocketReceived, ForwardingOutcome::Received,
        static_cast<uint64_t>(received), "");
    return {build_udp_response(
        *packet_template_, buffer.data(), static_cast<size_t>(received))};
}

}  // namespace redmiklab
