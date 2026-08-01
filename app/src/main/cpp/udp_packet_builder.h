#pragma once

#include "packet_parser.h"

#include <cstddef>
#include <cstdint>
#include <vector>

namespace redmiklab {

std::vector<uint8_t> build_udp_response(
    const PacketMetadata& request,
    const uint8_t* payload,
    size_t payload_length);

}  // namespace redmiklab
