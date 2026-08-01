#pragma once

#include "direct_udp_socket.h"
#include "forwarding_observation.h"
#include "packet_parser.h"

#include <memory>
#include <optional>
#include <vector>

namespace redmiklab {

class UdpProxySession {
public:
    explicit UdpProxySession(std::unique_ptr<UdpStream> stream, SessionObserver observer = {});

    bool on_client_packet(const std::vector<uint8_t>& packet);
    std::vector<std::vector<uint8_t>> poll();
    bool failed() const { return failed_; }

private:
    std::unique_ptr<UdpStream> stream_;
    SessionObserver observer_;
    std::optional<PacketMetadata> packet_template_;
    bool connected_ = false;
    bool failed_ = false;
};

}  // namespace redmiklab
