#include "tcp_packet_builder.h"
#include <array>
#include <cassert>
#include <string>

int main() {
    const redmiklab::PacketMetadata request{
        redmiklab::TransportProtocol::Tcp, 49152, 443, 0x0a000001, 0x08080808, 100, 0, 0x02, 40};
    const auto response = redmiklab::build_tcp_reset(request);
    assert(response.size() == 40);
    assert(response[12] == 8 && response[13] == 8 && response[14] == 8 && response[15] == 8);
    assert(response[16] == 10 && response[17] == 0 && response[18] == 0 && response[19] == 1);
    assert(response[20] == 0x01 && response[21] == 0xbb);
    assert(response[22] == 0xc0 && response[23] == 0x00);
    assert(response[33] == 0x14);

    const auto syn_ack = redmiklab::build_tcp_response(request, 500, 101, 0x12, nullptr, 0);
    assert(syn_ack.size() == 40);
    assert(syn_ack[24] == 0 && syn_ack[25] == 0 && syn_ack[26] == 1 && syn_ack[27] == 0xf4);
    assert(syn_ack[28] == 0 && syn_ack[29] == 0 && syn_ack[30] == 0 && syn_ack[31] == 101);
    assert(syn_ack[33] == 0x12);

    const std::string payload = "pong";
    const auto data = redmiklab::build_tcp_response(
        request,
        501,
        105,
        0x18,
        reinterpret_cast<const uint8_t*>(payload.data()),
        payload.size());
    assert(data.size() == 44);
    assert(std::string(data.begin() + 40, data.end()) == "pong");

    std::array<uint8_t, 16> ipv6_source{};
    std::array<uint8_t, 16> ipv6_destination{};
    ipv6_source[0] = 0x20;
    ipv6_source[1] = 0x01;
    ipv6_source[15] = 1;
    ipv6_destination[0] = 0x20;
    ipv6_destination[1] = 0x01;
    ipv6_destination[15] = 2;
    const redmiklab::PacketMetadata ipv6_request{
        redmiklab::TransportProtocol::Tcp, 49152, 443, 0, 0, 100, 0, 0x02, 60, 60, 0,
        16, ipv6_source, ipv6_destination};
    const auto ipv6_syn_ack = redmiklab::build_tcp_response(ipv6_request, 500, 101, 0x12, nullptr, 0);
    assert(ipv6_syn_ack.size() == 60);
    assert(ipv6_syn_ack[0] == 0x60);
    assert(ipv6_syn_ack[5] == 20 && ipv6_syn_ack[6] == 6);
    assert(ipv6_syn_ack[8] == 0x20 && ipv6_syn_ack[23] == 2);
    assert(ipv6_syn_ack[24] == 0x20 && ipv6_syn_ack[39] == 1);
    assert(ipv6_syn_ack[53] == 0x12);
}
