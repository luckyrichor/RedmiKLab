#pragma once

#include "packet_parser.h"
#include <cstddef>
#include <vector>

namespace redmiklab {
std::vector<uint8_t> build_tcp_response(
    const PacketMetadata& request,
    uint32_t sequence,
    uint32_t acknowledgement,
    uint8_t flags,
    const uint8_t* payload,
    size_t payload_length);
std::vector<uint8_t> build_tcp_reset(const PacketMetadata& request);
}
