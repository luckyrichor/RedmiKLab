#include "forwarder_engine.h"

#include "packet_parser.h"
#include "tls_sni_parser.h"

#include <algorithm>

namespace redmiklab {
namespace {
constexpr uint8_t kTcpProtocol = 6;
constexpr uint8_t kUdpProtocol = 17;
constexpr uint8_t kSyn = 0x02;
constexpr uint64_t kTcpIdleTimeoutMillis = 5 * 60 * 1000;
constexpr uint64_t kUdpIdleTimeoutMillis = 2 * 60 * 1000;

FlowKey flow_key(const PacketMetadata& metadata) {
    return FlowKey{
        metadata.protocol == TransportProtocol::Tcp ? kTcpProtocol : kUdpProtocol,
        metadata.source_address,
        metadata.source_port,
        metadata.destination_address,
        metadata.destination_port,
        metadata.address_length,
        metadata.source_ip,
        metadata.destination_ip};
}

void append_packets(
        std::vector<std::vector<uint8_t>>& destination,
        std::vector<std::vector<uint8_t>> source) {
    for (auto& packet : source) destination.push_back(std::move(packet));
}
}  // namespace

ForwarderEngine::ForwarderEngine(
        TcpFactory tcp_factory,
        UdpFactory udp_factory,
        FlowObserver flow_observer)
    : tcp_factory_(std::move(tcp_factory)),
      udp_factory_(std::move(udp_factory)),
      flow_observer_(std::move(flow_observer)) {}

std::vector<std::vector<uint8_t>> ForwarderEngine::on_tun_packet(
        const std::vector<uint8_t>& packet,
        uint64_t now_millis) {
    const auto metadata = parse_ip_packet(packet.data(), packet.size());
    if (!metadata.has_value()) return {};
    const auto key = flow_key(*metadata);
    if (metadata->protocol == TransportProtocol::Tcp && metadata->payload_bytes > 0) {
        const auto hostname = extract_tls_sni(
            packet.data() + metadata->payload_offset,
            metadata->payload_bytes);
        if (hostname.has_value()) hostnames_[key] = *hostname;
    }
    if (flow_observer_ &&
        (metadata->protocol == TransportProtocol::Tcp || metadata->protocol == TransportProtocol::Udp)) {
        observe(key, ForwardingDirection::Upstream, ForwardingStage::TunObserved,
            ForwardingOutcome::Observed, metadata->wire_bytes);
    }
    if (metadata->protocol == TransportProtocol::Tcp) {
        auto found = tcp_sessions_.find(key);
        if (found == tcp_sessions_.end()) {
            if ((metadata->tcp_flags & kSyn) == 0 || !tcp_factory_) {
                observe(key, ForwardingDirection::Upstream, ForwardingStage::Dropped,
                    ForwardingOutcome::Failed, metadata->payload_bytes, "NO_SESSION");
                return {};
            }
            auto stream = tcp_factory_();
            if (!stream) {
                observe(key, ForwardingDirection::Upstream, ForwardingStage::Dropped,
                    ForwardingOutcome::Failed, metadata->payload_bytes, "STREAM_CREATE_FAILED");
                return {};
            }
            auto session = std::make_unique<TcpProxySession>(
                std::move(stream), next_server_sequence_,
                [this, key](auto direction, auto stage, auto outcome, uint64_t bytes, const std::string& reason) {
                    observe(key, direction, stage, outcome, bytes, reason);
                });
            next_server_sequence_ += 1000003;
            found = tcp_sessions_.emplace(key, TcpEntry{std::move(session), now_millis}).first;
        }
        found->second.last_activity_millis = now_millis;
        return found->second.session->on_client_packet(packet);
    }
    if (metadata->protocol == TransportProtocol::Udp) {
        auto found = udp_sessions_.find(key);
        if (found == udp_sessions_.end()) {
            if (!udp_factory_) {
                observe(key, ForwardingDirection::Upstream, ForwardingStage::Dropped,
                    ForwardingOutcome::Failed, metadata->payload_bytes, "NO_SESSION");
                return {};
            }
            auto stream = udp_factory_();
            if (!stream) {
                observe(key, ForwardingDirection::Upstream, ForwardingStage::Dropped,
                    ForwardingOutcome::Failed, metadata->payload_bytes, "STREAM_CREATE_FAILED");
                return {};
            }
            auto session = std::make_unique<UdpProxySession>(
                std::move(stream),
                [this, key](auto direction, auto stage, auto outcome, uint64_t bytes, const std::string& reason) {
                    observe(key, direction, stage, outcome, bytes, reason);
                });
            found = udp_sessions_.emplace(key, UdpEntry{std::move(session), now_millis}).first;
        }
        found->second.last_activity_millis = now_millis;
        found->second.session->on_client_packet(packet);
    }
    return {};
}

std::vector<std::vector<uint8_t>> ForwarderEngine::poll(uint64_t now_millis) {
    std::vector<std::vector<uint8_t>> responses;
    for (auto& [key, entry] : tcp_sessions_) {
        auto packets = entry.session->poll(now_millis);
        if (!packets.empty()) entry.last_activity_millis = now_millis;
        append_packets(responses, std::move(packets));
    }
    for (auto& [key, entry] : udp_sessions_) {
        auto packets = entry.session->poll();
        if (!packets.empty()) entry.last_activity_millis = now_millis;
        append_packets(responses, std::move(packets));
    }
    for (auto iterator = tcp_sessions_.begin(); iterator != tcp_sessions_.end();) {
        if (iterator->second.session->closed() ||
            now_millis - iterator->second.last_activity_millis > kTcpIdleTimeoutMillis) {
            const auto expired_key = iterator->first;
            iterator = tcp_sessions_.erase(iterator);
            hostnames_.erase(expired_key);
        } else {
            ++iterator;
        }
    }
    for (auto iterator = udp_sessions_.begin(); iterator != udp_sessions_.end();) {
        if (iterator->second.session->failed() ||
            now_millis - iterator->second.last_activity_millis > kUdpIdleTimeoutMillis) {
            iterator = udp_sessions_.erase(iterator);
        } else {
            ++iterator;
        }
    }
    return responses;
}

void ForwarderEngine::observe(
        const FlowKey& key,
        ForwardingDirection direction,
        ForwardingStage stage,
        ForwardingOutcome outcome,
        uint64_t bytes,
        const std::string& reason) {
    if (!flow_observer_ || (bytes == 0 && outcome != ForwardingOutcome::Failed)) return;
    flow_observer_(ForwardingObservation{
        key, direction, stage, outcome, bytes, reason, hostnames_[key]});
}

}  // namespace redmiklab
