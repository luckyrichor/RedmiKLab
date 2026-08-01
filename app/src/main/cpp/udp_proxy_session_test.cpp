#include "udp_proxy_session.h"

#include <algorithm>
#include <cassert>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

namespace {
class FakeUdpStream final : public redmiklab::UdpStream {
public:
    bool connect_to(const redmiklab::IpAddress& address, uint16_t port) override {
        connected_address = address;
        connected_port = port;
        return true;
    }
    ssize_t send_datagram(const uint8_t* bytes, size_t length) override {
        sent.assign(bytes, bytes + length);
        return static_cast<ssize_t>(length);
    }
    ssize_t receive_datagram(uint8_t* bytes, size_t capacity) override {
        if (received || capacity < response.size()) return -1;
        std::memcpy(bytes, response.data(), response.size());
        received = true;
        return static_cast<ssize_t>(response.size());
    }
    int file_descriptor() const override { return 8; }

    redmiklab::IpAddress connected_address;
    uint16_t connected_port = 0;
    std::vector<uint8_t> sent;
    std::string response = "answer";
    bool received = false;
};

std::vector<uint8_t> udp_packet(const std::string& payload) {
    std::vector<uint8_t> packet(28 + payload.size(), 0);
    packet[0] = 0x45;
    packet[2] = static_cast<uint8_t>(packet.size() >> 8);
    packet[3] = static_cast<uint8_t>(packet.size());
    packet[8] = 64;
    packet[9] = 17;
    packet[12] = 10;
    packet[15] = 2;
    packet[16] = 8;
    packet[17] = 8;
    packet[18] = 8;
    packet[19] = 8;
    packet[20] = 0xcf;
    packet[21] = 0x08;
    packet[22] = 0;
    packet[23] = 53;
    const uint16_t udp_length = static_cast<uint16_t>(8 + payload.size());
    packet[24] = static_cast<uint8_t>(udp_length >> 8);
    packet[25] = static_cast<uint8_t>(udp_length);
    std::copy(payload.begin(), payload.end(), packet.begin() + 28);
    return packet;
}
}  // namespace

int main() {
    auto fake = std::make_unique<FakeUdpStream>();
    auto* stream = fake.get();
    redmiklab::UdpProxySession session(std::move(fake));
    assert(session.on_client_packet(udp_packet("query")));
    assert(stream->connected_address == redmiklab::IpAddress::ipv4(0x08080808));
    assert(stream->connected_port == 53);
    assert(std::string(stream->sent.begin(), stream->sent.end()) == "query");
    const auto responses = session.poll();
    assert(responses.size() == 1);
    assert(std::string(responses[0].begin() + 28, responses[0].end()) == "answer");
}
