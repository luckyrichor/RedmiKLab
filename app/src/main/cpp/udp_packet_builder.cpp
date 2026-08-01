#include "udp_packet_builder.h"

#include "checksum.h"

#include <algorithm>
#include <limits>

namespace redmiklab {
namespace {
constexpr size_t kIpv4HeaderBytes = 20;
constexpr size_t kIpv6HeaderBytes = 40;
constexpr size_t kUdpHeaderBytes = 8;

void write_u16(uint8_t* bytes, uint16_t value) {
    bytes[0] = static_cast<uint8_t>(value >> 8);
    bytes[1] = static_cast<uint8_t>(value);
}

void write_u32(uint8_t* bytes, uint32_t value) {
    bytes[0] = static_cast<uint8_t>(value >> 24);
    bytes[1] = static_cast<uint8_t>(value >> 16);
    bytes[2] = static_cast<uint8_t>(value >> 8);
    bytes[3] = static_cast<uint8_t>(value);
}
}  // namespace

std::vector<uint8_t> build_udp_response(
        const PacketMetadata& request,
        const uint8_t* payload,
        size_t payload_length) {
    const bool ipv6 = request.address_length == 16;
    const size_t ip_header_bytes = ipv6 ? kIpv6HeaderBytes : kIpv4HeaderBytes;
    const size_t kMaximumPayload = std::numeric_limits<uint16_t>::max() -
        (ipv6 ? kUdpHeaderBytes : ip_header_bytes + kUdpHeaderBytes);
    if (payload_length > kMaximumPayload || (payload_length > 0 && payload == nullptr)) return {};
    const size_t udp_length = kUdpHeaderBytes + payload_length;
    std::vector<uint8_t> packet(ip_header_bytes + udp_length, 0);
    if (ipv6) {
        packet[0] = 0x60;
        write_u16(packet.data() + 4, static_cast<uint16_t>(udp_length));
        packet[6] = 17;
        packet[7] = 64;
        std::copy(request.destination_ip.begin(), request.destination_ip.end(), packet.begin() + 8);
        std::copy(request.source_ip.begin(), request.source_ip.end(), packet.begin() + 24);
    } else {
        packet[0] = 0x45;
        write_u16(packet.data() + 2, static_cast<uint16_t>(packet.size()));
        packet[6] = 0x40;
        packet[8] = 64;
        packet[9] = 17;
        write_u32(packet.data() + 12, request.destination_address);
        write_u32(packet.data() + 16, request.source_address);
    }
    const size_t udp_offset = ip_header_bytes;
    write_u16(packet.data() + udp_offset, request.destination_port);
    write_u16(packet.data() + udp_offset + 2, request.source_port);
    write_u16(packet.data() + udp_offset + 4, static_cast<uint16_t>(udp_length));
    if (payload_length > 0) {
        std::copy(payload, payload + payload_length, packet.begin() + ip_header_bytes + kUdpHeaderBytes);
    }
    if (!ipv6) write_u16(packet.data() + 10, internet_checksum(packet.data(), kIpv4HeaderBytes));

    const size_t pseudo_header_bytes = ipv6 ? 40 : 12;
    std::vector<uint8_t> pseudo(pseudo_header_bytes + udp_length, 0);
    if (ipv6) {
        std::copy(packet.begin() + 8, packet.begin() + 40, pseudo.begin());
        write_u32(pseudo.data() + 32, static_cast<uint32_t>(udp_length));
        pseudo[39] = 17;
    } else {
        std::copy(packet.begin() + 12, packet.begin() + 20, pseudo.begin());
        pseudo[9] = 17;
        write_u16(pseudo.data() + 10, static_cast<uint16_t>(udp_length));
    }
    std::copy(packet.begin() + ip_header_bytes, packet.end(), pseudo.begin() + pseudo_header_bytes);
    uint16_t udp_checksum = internet_checksum(pseudo.data(), pseudo.size());
    if (udp_checksum == 0) udp_checksum = 0xffff;
    write_u16(packet.data() + udp_offset + 6, udp_checksum);
    return packet;
}

}  // namespace redmiklab
