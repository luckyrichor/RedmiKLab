#include "packet_parser.h"
#include <cassert>
#include <cstdint>
#include <vector>

int main() {
    const std::vector<uint8_t> packet = {
        0x45, 0x00, 0x00, 0x28, 0x00, 0x00, 0x40, 0x00, 0x40, 0x06, 0x00, 0x00,
        192, 168, 1, 10, 1, 1, 1, 1,
        0x1f, 0x90, 0x01, 0xbb, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x50, 0x02, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00,
    };
    const auto metadata = redmiklab::parse_ipv4_packet(packet.data(), packet.size());
    assert(metadata.has_value());
    assert(metadata->protocol == redmiklab::TransportProtocol::Tcp);
    assert(metadata->source_port == 8080);
    assert(metadata->destination_port == 443);
    assert(metadata->source_address == 0xc0a8010a);
    assert(metadata->destination_address == 0x01010101);
    assert(metadata->tcp_sequence == 0);
    assert(metadata->tcp_acknowledgement == 0);
    assert(metadata->tcp_flags == 0x02);
    assert(metadata->wire_bytes == 40);

    auto data_packet = packet;
    data_packet[3] = 0x2c;
    data_packet.insert(data_packet.end(), {'p', 'i', 'n', 'g'});
    const auto data_metadata = redmiklab::parse_ipv4_packet(data_packet.data(), data_packet.size());
    assert(data_metadata.has_value());
    assert(data_metadata->payload_offset == 40);
    assert(data_metadata->payload_bytes == 4);

    auto fragment = data_packet;
    fragment[6] = 0x20;
    assert(!redmiklab::parse_ipv4_packet(fragment.data(), fragment.size()).has_value());

    std::vector<uint8_t> udp(32, 0);
    udp[0] = 0x45;
    udp[2] = 0;
    udp[3] = 32;
    udp[9] = 17;
    udp[20] = 0xcf;
    udp[21] = 0x08;
    udp[23] = 53;
    udp[24] = 0;
    udp[25] = 12;
    const auto udp_metadata = redmiklab::parse_ipv4_packet(udp.data(), udp.size());
    assert(udp_metadata.has_value());
    assert(udp_metadata->payload_offset == 28);
    assert(udp_metadata->payload_bytes == 4);

    udp[25] = 20;
    assert(!redmiklab::parse_ipv4_packet(udp.data(), udp.size()).has_value());

    std::vector<uint8_t> ipv6_udp(52, 0);
    ipv6_udp[0] = 0x60;
    ipv6_udp[5] = 12;
    ipv6_udp[6] = 17;
    ipv6_udp[7] = 64;
    ipv6_udp[8] = 0x20;
    ipv6_udp[9] = 0x01;
    ipv6_udp[10] = 0x0d;
    ipv6_udp[11] = 0xb8;
    ipv6_udp[23] = 1;
    ipv6_udp[24] = 0x20;
    ipv6_udp[25] = 0x01;
    ipv6_udp[26] = 0x0d;
    ipv6_udp[27] = 0xb8;
    ipv6_udp[39] = 2;
    ipv6_udp[40] = 0xcf;
    ipv6_udp[41] = 0x08;
    ipv6_udp[43] = 53;
    ipv6_udp[45] = 12;
    const auto ipv6_metadata = redmiklab::parse_ip_packet(ipv6_udp.data(), ipv6_udp.size());
    assert(ipv6_metadata.has_value());
    assert(ipv6_metadata->address_length == 16);
    assert(ipv6_metadata->source_ip[0] == 0x20 && ipv6_metadata->source_ip[15] == 1);
    assert(ipv6_metadata->destination_ip[0] == 0x20 && ipv6_metadata->destination_ip[15] == 2);
    assert(ipv6_metadata->payload_offset == 48);
    assert(ipv6_metadata->payload_bytes == 4);

    ipv6_udp[6] = 44;
    assert(!redmiklab::parse_ip_packet(ipv6_udp.data(), ipv6_udp.size()).has_value());
}
