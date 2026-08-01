#include "packet_parser.h"

#include <algorithm>

namespace redmiklab {
namespace {
constexpr size_t kIpv4MinimumHeaderBytes = 20;
constexpr size_t kIpv6HeaderBytes = 40;
constexpr uint8_t kIpv4Version = 4;
constexpr uint8_t kIpv6Version = 6;
constexpr uint8_t kTcpProtocol = 6;
constexpr uint8_t kUdpProtocol = 17;

uint16_t read_u16(const uint8_t* bytes) {
    return static_cast<uint16_t>((static_cast<uint16_t>(bytes[0]) << 8) | bytes[1]);
}

uint32_t read_u32(const uint8_t* bytes) {
    return (static_cast<uint32_t>(bytes[0]) << 24) |
        (static_cast<uint32_t>(bytes[1]) << 16) |
        (static_cast<uint32_t>(bytes[2]) << 8) |
        bytes[3];
}

std::optional<PacketMetadata> parse_transport(
        const uint8_t* packet,
        size_t header_bytes,
        size_t total_bytes,
        uint8_t protocol_number,
        uint32_t source_address,
        uint32_t destination_address,
        uint8_t address_length,
        const std::array<uint8_t, 16>& source_ip,
        const std::array<uint8_t, 16>& destination_ip) {
    const auto protocol = protocol_number == kTcpProtocol ? TransportProtocol::Tcp :
        protocol_number == kUdpProtocol ? TransportProtocol::Udp : TransportProtocol::Other;
    if (protocol == TransportProtocol::Tcp && header_bytes + 20 > total_bytes) return std::nullopt;
    if (protocol == TransportProtocol::Udp && header_bytes + 8 > total_bytes) return std::nullopt;
    if (protocol == TransportProtocol::Other) {
        return PacketMetadata{protocol, 0, 0, source_address, destination_address, 0, 0, 0,
            static_cast<uint16_t>(total_bytes), static_cast<uint16_t>(total_bytes), 0,
            address_length, source_ip, destination_ip};
    }
    const uint32_t tcp_sequence = protocol == TransportProtocol::Tcp ? read_u32(packet + header_bytes + 4) : 0;
    const uint32_t tcp_acknowledgement = protocol == TransportProtocol::Tcp ? read_u32(packet + header_bytes + 8) : 0;
    const uint8_t tcp_flags = protocol == TransportProtocol::Tcp ? packet[header_bytes + 13] : 0;
    const uint16_t udp_bytes = protocol == TransportProtocol::Udp ? read_u16(packet + header_bytes + 4) : 0;
    if (protocol == TransportProtocol::Udp && (udp_bytes < 8 || header_bytes + udp_bytes > total_bytes)) return std::nullopt;
    const size_t transport_header_bytes = protocol == TransportProtocol::Tcp ?
        static_cast<size_t>(packet[header_bytes + 12] >> 4) * 4 : 8;
    if ((protocol == TransportProtocol::Tcp && transport_header_bytes < 20) ||
        header_bytes + transport_header_bytes > total_bytes) return std::nullopt;
    const uint16_t payload_offset = static_cast<uint16_t>(header_bytes + transport_header_bytes);
    const uint16_t payload_bytes = protocol == TransportProtocol::Udp ?
        static_cast<uint16_t>(udp_bytes - transport_header_bytes) :
        static_cast<uint16_t>(total_bytes - payload_offset);
    return PacketMetadata{
        protocol,
        read_u16(packet + header_bytes),
        read_u16(packet + header_bytes + 2),
        source_address,
        destination_address,
        tcp_sequence,
        tcp_acknowledgement,
        tcp_flags,
        static_cast<uint16_t>(total_bytes),
        payload_offset,
        payload_bytes,
        address_length,
        source_ip,
        destination_ip};
}
}  // namespace

std::optional<PacketMetadata> parse_ipv4_packet(const uint8_t* packet, size_t length) {
    if (packet == nullptr || length < kIpv4MinimumHeaderBytes || (packet[0] >> 4) != kIpv4Version) return std::nullopt;
    const size_t header_bytes = static_cast<size_t>(packet[0] & 0x0f) * 4;
    const uint16_t total_bytes = read_u16(packet + 2);
    const uint16_t fragment = read_u16(packet + 6);
    if (header_bytes < kIpv4MinimumHeaderBytes || header_bytes + 4 > length || total_bytes < header_bytes || total_bytes > length) return std::nullopt;
    if ((fragment & 0x3fff) != 0) return std::nullopt;
    const uint32_t source_address = read_u32(packet + 12);
    const uint32_t destination_address = read_u32(packet + 16);
    std::array<uint8_t, 16> source_ip{};
    std::array<uint8_t, 16> destination_ip{};
    std::copy(packet + 12, packet + 16, source_ip.begin());
    std::copy(packet + 16, packet + 20, destination_ip.begin());
    return parse_transport(packet, header_bytes, total_bytes, packet[9], source_address,
        destination_address, 4, source_ip, destination_ip);
}

std::optional<PacketMetadata> parse_ip_packet(const uint8_t* packet, size_t length) {
    if (packet == nullptr || length == 0) return std::nullopt;
    const uint8_t version = packet[0] >> 4;
    if (version == kIpv4Version) return parse_ipv4_packet(packet, length);
    if (version != kIpv6Version || length < kIpv6HeaderBytes) return std::nullopt;
    const size_t total_bytes = kIpv6HeaderBytes + read_u16(packet + 4);
    if (total_bytes > length || total_bytes > UINT16_MAX) return std::nullopt;
    const uint8_t next_header = packet[6];
    if (next_header != kTcpProtocol && next_header != kUdpProtocol) return std::nullopt;
    std::array<uint8_t, 16> source_ip{};
    std::array<uint8_t, 16> destination_ip{};
    std::copy(packet + 8, packet + 24, source_ip.begin());
    std::copy(packet + 24, packet + 40, destination_ip.begin());
    return parse_transport(packet, kIpv6HeaderBytes, total_bytes, next_header, 0, 0, 16,
        source_ip, destination_ip);
}

}  // namespace redmiklab
