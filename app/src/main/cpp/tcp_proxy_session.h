#pragma once

#include "direct_tcp_socket.h"
#include "forwarding_observation.h"
#include "packet_parser.h"

#include <cstdint>
#include <memory>
#include <optional>
#include <vector>

namespace redmiklab {

class TcpProxySession {
public:
    TcpProxySession(
        std::unique_ptr<TcpStream> stream,
        uint32_t initial_server_sequence,
        SessionObserver observer = {});

    std::vector<std::vector<uint8_t>> on_client_packet(const std::vector<uint8_t>& packet);
    std::vector<std::vector<uint8_t>> poll(uint64_t now_millis = 0);
    bool closed() const { return closed_; }

private:
    std::vector<std::vector<uint8_t>> complete_connect();

    std::unique_ptr<TcpStream> stream_;
    SessionObserver observer_;
    std::optional<PacketMetadata> packet_template_;
    uint32_t initial_server_sequence_;
    uint32_t server_next_sequence_;
    uint32_t client_initial_sequence_ = 0;
    uint32_t client_next_sequence_ = 0;
    bool connecting_ = false;
    bool connected_ = false;
    bool closed_ = false;
    bool client_fin_received_ = false;
    bool remote_fin_sent_ = false;
    bool remote_fin_acknowledged_ = false;
    std::vector<uint8_t> pending_server_packet_;
    uint32_t pending_server_end_sequence_ = 0;
    uint64_t pending_server_sent_at_millis_ = 0;
    uint8_t pending_server_retransmissions_ = 0;
};

}  // namespace redmiklab
