#include "tcp_packet_builder.h"

#include "checksum.h"
#include <limits>

namespace redmiklab {
namespace {
constexpr size_t kIpv4HeaderBytes = 20;
constexpr size_t kIpv6HeaderBytes = 40;
constexpr size_t kTcpHeaderBytes = 20;

void write_u16(uint8_t* bytes, uint16_t value) {
    bytes[0] = static_cast<uint8_t>(value >> 8);
    bytes[1] = static_cast<uint8_t>(value & 0xff);
}

void write_u32(uint8_t* bytes, uint32_t value) {
    bytes[0] = static_cast<uint8_t>(value >> 24);
    bytes[1] = static_cast<uint8_t>(value >> 16);
    bytes[2] = static_cast<uint8_t>(value >> 8);
    bytes[3] = static_cast<uint8_t>(value & 0xff);
}
}  // namespace

std::vector<uint8_t> build_tcp_response(
        const PacketMetadata& request,
        uint32_t sequence,
        uint32_t acknowledgement,
        uint8_t flags,
        const uint8_t* payload,
        size_t payload_length) {
    const bool ipv6 = request.address_length == 16;
    const size_t ip_header_bytes = ipv6 ? kIpv6HeaderBytes : kIpv4HeaderBytes;
    const size_t kMaximumPayload = std::numeric_limits<uint16_t>::max() - ip_header_bytes - kTcpHeaderBytes;
    if (payload_length > kMaximumPayload || (payload_length > 0 && payload == nullptr)) return {};
    const size_t tcp_length = kTcpHeaderBytes + payload_length;
    std::vector<uint8_t> packet(ip_header_bytes + tcp_length, 0);
    if (ipv6) {
        packet[0] = 0x60;
        write_u16(packet.data() + 4, static_cast<uint16_t>(tcp_length));
        packet[6] = 6;
        packet[7] = 64;
        std::copy(request.destination_ip.begin(), request.destination_ip.end(), packet.begin() + 8);
        std::copy(request.source_ip.begin(), request.source_ip.end(), packet.begin() + 24);
    } else {
        packet[0] = 0x45;
        write_u16(packet.data() + 2, static_cast<uint16_t>(packet.size()));
        packet[6] = 0x40;
        packet[8] = 64;
        packet[9] = 6;
        write_u32(packet.data() + 12, request.destination_address);
        write_u32(packet.data() + 16, request.source_address);
    }
    const size_t tcp_offset = ip_header_bytes;
    write_u16(packet.data() + tcp_offset, request.destination_port);
    write_u16(packet.data() + tcp_offset + 2, request.source_port);
    write_u32(packet.data() + tcp_offset + 4, sequence);
    write_u32(packet.data() + tcp_offset + 8, acknowledgement);
    packet[tcp_offset + 12] = 0x50;
    packet[tcp_offset + 13] = flags;
    write_u16(packet.data() + tcp_offset + 14, 65535);
    if (payload_length > 0) {
        std::copy(payload, payload + payload_length, packet.begin() + ip_header_bytes + kTcpHeaderBytes);
    }
    if (!ipv6) write_u16(packet.data() + 10, internet_checksum(packet.data(), kIpv4HeaderBytes));

    const size_t pseudo_header_bytes = ipv6 ? 40 : 12;
    std::vector<uint8_t> pseudo(pseudo_header_bytes + tcp_length, 0);
    if (ipv6) {
        std::copy(packet.begin() + 8, packet.begin() + 40, pseudo.begin());
        write_u32(pseudo.data() + 32, static_cast<uint32_t>(tcp_length));
        pseudo[39] = 6;
    } else {
        for (size_t i = 0; i < 4; ++i) {
            pseudo[i] = packet[12 + i];
            pseudo[4 + i] = packet[16 + i];
        }
        pseudo[9] = 6;
        write_u16(pseudo.data() + 10, static_cast<uint16_t>(tcp_length));
    }
    std::copy(packet.begin() + ip_header_bytes, packet.end(), pseudo.begin() + pseudo_header_bytes);
    write_u16(packet.data() + tcp_offset + 16, internet_checksum(pseudo.data(), pseudo.size()));
    return packet;
}

std::vector<uint8_t> build_tcp_reset(const PacketMetadata& request) {
    return build_tcp_response(request, 0, request.tcp_sequence + 1, 0x14, nullptr, 0);
}

}  // namespace redmiklab
