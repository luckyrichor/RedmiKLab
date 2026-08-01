#pragma once

#include "flow_table.h"
#include "forwarding_observation.h"
#include "tcp_proxy_session.h"
#include "udp_proxy_session.h"

#include <cstdint>
#include <functional>
#include <memory>
#include <unordered_map>
#include <string>
#include <vector>

namespace redmiklab {

class ForwarderEngine {
public:
    using TcpFactory = std::function<std::unique_ptr<TcpStream>()>;
    using UdpFactory = std::function<std::unique_ptr<UdpStream>()>;
    using FlowObserver = std::function<void(const ForwardingObservation&)>;

    ForwarderEngine(TcpFactory tcp_factory, UdpFactory udp_factory, FlowObserver flow_observer = {});

    std::vector<std::vector<uint8_t>> on_tun_packet(
        const std::vector<uint8_t>& packet,
        uint64_t now_millis);
    std::vector<std::vector<uint8_t>> poll(uint64_t now_millis);
    size_t tcp_session_count() const { return tcp_sessions_.size(); }
    size_t udp_session_count() const { return udp_sessions_.size(); }

private:
    struct TcpEntry {
        std::unique_ptr<TcpProxySession> session;
        uint64_t last_activity_millis;
    };
    struct UdpEntry {
        std::unique_ptr<UdpProxySession> session;
        uint64_t last_activity_millis;
    };

    TcpFactory tcp_factory_;
    UdpFactory udp_factory_;
    FlowObserver flow_observer_;
    std::unordered_map<FlowKey, TcpEntry, FlowKeyHash> tcp_sessions_;
    std::unordered_map<FlowKey, UdpEntry, FlowKeyHash> udp_sessions_;
    std::unordered_map<FlowKey, std::string, FlowKeyHash> hostnames_;
    uint32_t next_server_sequence_ = 500;

    void observe(
        const FlowKey& key,
        ForwardingDirection direction,
        ForwardingStage stage,
        ForwardingOutcome outcome,
        uint64_t bytes,
        const std::string& reason = "");
};

}  // namespace redmiklab
