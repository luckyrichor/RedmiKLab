#include "forwarder_runtime.h"

#include <arpa/inet.h>
#include <array>
#include <cassert>
#include <chrono>
#include <cstring>
#include <netinet/in.h>
#include <string>
#include <sys/socket.h>
#include <thread>
#include <unistd.h>
#include <vector>

namespace {
std::vector<uint8_t> udp_packet(uint16_t destination_port, const std::string& payload) {
    std::vector<uint8_t> packet(28 + payload.size(), 0);
    packet[0] = 0x45;
    packet[2] = static_cast<uint8_t>(packet.size() >> 8);
    packet[3] = static_cast<uint8_t>(packet.size());
    packet[8] = 64;
    packet[9] = 17;
    packet[12] = 10;
    packet[15] = 2;
    packet[16] = 127;
    packet[19] = 1;
    packet[20] = 0xcf;
    packet[21] = 0x08;
    packet[22] = static_cast<uint8_t>(destination_port >> 8);
    packet[23] = static_cast<uint8_t>(destination_port);
    const uint16_t udp_length = static_cast<uint16_t>(8 + payload.size());
    packet[24] = static_cast<uint8_t>(udp_length >> 8);
    packet[25] = static_cast<uint8_t>(udp_length);
    std::copy(payload.begin(), payload.end(), packet.begin() + 28);
    return packet;
}

std::vector<uint8_t> ipv6_udp_packet(uint16_t destination_port, const std::string& payload) {
    std::vector<uint8_t> packet(48 + payload.size(), 0);
    packet[0] = 0x60;
    const uint16_t payload_length = static_cast<uint16_t>(8 + payload.size());
    packet[4] = static_cast<uint8_t>(payload_length >> 8);
    packet[5] = static_cast<uint8_t>(payload_length);
    packet[6] = 17;
    packet[7] = 64;
    packet[8] = 0xfd;
    packet[23] = 2;
    packet[39] = 1;
    packet[40] = 0xcf;
    packet[41] = 0x09;
    packet[42] = static_cast<uint8_t>(destination_port >> 8);
    packet[43] = static_cast<uint8_t>(destination_port);
    packet[44] = static_cast<uint8_t>(payload_length >> 8);
    packet[45] = static_cast<uint8_t>(payload_length);
    std::copy(payload.begin(), payload.end(), packet.begin() + 48);
    return packet;
}
}  // namespace

int main() {
    int tunnel[2]{};
    assert(socketpair(AF_UNIX, SOCK_DGRAM, 0, tunnel) == 0);
    const int server = socket(AF_INET, SOCK_DGRAM, 0);
    assert(server >= 0);
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    assert(bind(server, reinterpret_cast<sockaddr*>(&address), sizeof(address)) == 0);
    socklen_t address_size = sizeof(address);
    assert(getsockname(server, reinterpret_cast<sockaddr*>(&address), &address_size) == 0);

    bool protected_socket = false;
    uint64_t tunnel_observed_bytes = 0;
    uint64_t upstream_accepted_bytes = 0;
    uint64_t downstream_received_bytes = 0;
    redmiklab::ForwarderRuntime runtime(tunnel[1], [&](int) {
        protected_socket = true;
        return true;
    }, [&](const redmiklab::ForwardingObservation& observation) {
        if (observation.stage == redmiklab::ForwardingStage::TunObserved) {
            tunnel_observed_bytes += observation.bytes;
        } else if (observation.stage == redmiklab::ForwardingStage::UpstreamSocketAccepted) {
            upstream_accepted_bytes += observation.bytes;
        } else if (observation.stage == redmiklab::ForwardingStage::DownstreamSocketReceived) {
            downstream_received_bytes += observation.bytes;
        }
    });
    assert(runtime.start());
    const auto query = udp_packet(ntohs(address.sin_port), "query");
    assert(write(tunnel[0], query.data(), query.size()) == static_cast<ssize_t>(query.size()));

    std::array<uint8_t, 64> buffer{};
    sockaddr_in client{};
    socklen_t client_size = sizeof(client);
    assert(recvfrom(server, buffer.data(), buffer.size(), 0,
        reinterpret_cast<sockaddr*>(&client), &client_size) == 5);
    assert(std::string(buffer.begin(), buffer.begin() + 5) == "query");
    assert(sendto(server, "answer", 6, 0, reinterpret_cast<sockaddr*>(&client), client_size) == 6);

    ssize_t response_size = -1;
    for (int attempt = 0; response_size < 0 && attempt < 100; ++attempt) {
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        response_size = recv(tunnel[0], buffer.data(), buffer.size(), MSG_DONTWAIT);
    }
    assert(response_size == 34);
    assert(std::string(buffer.begin() + 28, buffer.begin() + response_size) == "answer");
    assert(protected_socket);
    assert(tunnel_observed_bytes == query.size());
    assert(upstream_accepted_bytes == 5);
    assert(downstream_received_bytes == 6);

    const int server6 = socket(AF_INET6, SOCK_DGRAM, 0);
    assert(server6 >= 0);
    sockaddr_in6 address6{};
    address6.sin6_family = AF_INET6;
    address6.sin6_addr = in6addr_loopback;
    assert(bind(server6, reinterpret_cast<sockaddr*>(&address6), sizeof(address6)) == 0);
    socklen_t address6_size = sizeof(address6);
    assert(getsockname(server6, reinterpret_cast<sockaddr*>(&address6), &address6_size) == 0);
    const auto query6 = ipv6_udp_packet(ntohs(address6.sin6_port), "query6");
    assert(write(tunnel[0], query6.data(), query6.size()) == static_cast<ssize_t>(query6.size()));
    sockaddr_in6 client6{};
    socklen_t client6_size = sizeof(client6);
    assert(recvfrom(server6, buffer.data(), buffer.size(), 0,
        reinterpret_cast<sockaddr*>(&client6), &client6_size) == 6);
    assert(sendto(server6, "answer6", 7, 0, reinterpret_cast<sockaddr*>(&client6), client6_size) == 7);
    response_size = -1;
    for (int attempt = 0; response_size < 0 && attempt < 100; ++attempt) {
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        response_size = recv(tunnel[0], buffer.data(), buffer.size(), MSG_DONTWAIT);
    }
    assert(response_size == 55);
    assert(buffer[0] == 0x60);
    assert(std::string(buffer.begin() + 48, buffer.begin() + response_size) == "answer6");
    runtime.stop();
    close(server6);
    close(server);
    close(tunnel[0]);
    close(tunnel[1]);
}
