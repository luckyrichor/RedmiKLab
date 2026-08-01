#pragma once

#include "ip_address.h"

#include <array>
#include <cstddef>
#include <cstdint>
#include <optional>

namespace redmiklab {

enum class TransportProtocol { Tcp, Udp, Other };

struct PacketMetadata {
    TransportProtocol protocol;
    uint16_t source_port;
    uint16_t destination_port;
    uint32_t source_address;
    uint32_t destination_address;
    uint32_t tcp_sequence;
    uint32_t tcp_acknowledgement;
    uint8_t tcp_flags;
    uint16_t wire_bytes;
    uint16_t payload_offset;
    uint16_t payload_bytes;
    uint8_t address_length;
    std::array<uint8_t, 16> source_ip;
    std::array<uint8_t, 16> destination_ip;
};

std::optional<PacketMetadata> parse_ipv4_packet(const uint8_t* packet, size_t length);
std::optional<PacketMetadata> parse_ip_packet(const uint8_t* packet, size_t length);

inline IpAddress source_ip_address(const PacketMetadata& metadata) {
    return metadata.address_length == 16 ? IpAddress::ipv6(metadata.source_ip.data()) :
        IpAddress::ipv4(metadata.source_address);
}

inline IpAddress destination_ip_address(const PacketMetadata& metadata) {
    return metadata.address_length == 16 ? IpAddress::ipv6(metadata.destination_ip.data()) :
        IpAddress::ipv4(metadata.destination_address);
}

}  // namespace redmiklab
