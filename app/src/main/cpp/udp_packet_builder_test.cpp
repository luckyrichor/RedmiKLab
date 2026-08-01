#include "checksum.h"
#include "udp_packet_builder.h"

#include <cassert>
#include <array>
#include <string>

int main() {
    const redmiklab::PacketMetadata request{
        redmiklab::TransportProtocol::Udp,
        53000,
        53,
        0x0a000002,
        0x08080808,
        0,
        0,
        0,
        32,
        28,
        4};
    const std::string payload = "dns!";
    const auto response = redmiklab::build_udp_response(
        request,
        reinterpret_cast<const uint8_t*>(payload.data()),
        payload.size());
    assert(response.size() == 32);
    assert(response[12] == 8 && response[13] == 8 && response[14] == 8 && response[15] == 8);
    assert(response[16] == 10 && response[17] == 0 && response[18] == 0 && response[19] == 2);
    assert(response[20] == 0 && response[21] == 53);
    assert(response[22] == 0xcf && response[23] == 0x08);
    assert(std::string(response.begin() + 28, response.end()) == payload);
    assert(redmiklab::internet_checksum(response.data(), 20) == 0);

    std::array<uint8_t, 16> ipv6_source{};
    std::array<uint8_t, 16> ipv6_destination{};
    ipv6_source[0] = 0x20;
    ipv6_source[1] = 0x01;
    ipv6_source[15] = 1;
    ipv6_destination[0] = 0x20;
    ipv6_destination[1] = 0x01;
    ipv6_destination[15] = 2;
    const redmiklab::PacketMetadata ipv6_request{
        redmiklab::TransportProtocol::Udp, 53000, 53, 0, 0, 0, 0, 0, 52, 48, 4,
        16, ipv6_source, ipv6_destination};
    const auto ipv6_response = redmiklab::build_udp_response(
        ipv6_request,
        reinterpret_cast<const uint8_t*>(payload.data()),
        payload.size());
    assert(ipv6_response.size() == 52);
    assert(ipv6_response[0] == 0x60);
    assert(ipv6_response[5] == 12 && ipv6_response[6] == 17);
    assert(ipv6_response[8] == 0x20 && ipv6_response[23] == 2);
    assert(ipv6_response[24] == 0x20 && ipv6_response[39] == 1);
    assert(std::string(ipv6_response.begin() + 48, ipv6_response.end()) == payload);
}
