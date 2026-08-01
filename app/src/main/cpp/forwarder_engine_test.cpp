#include "forwarder_engine.h"

#include <algorithm>
#include <cassert>
#include <cstring>
#include <deque>
#include <memory>
#include <string>
#include <vector>

namespace {
struct TcpState {
    std::vector<uint8_t> sent;
    std::deque<std::string> incoming;
};

class FakeTcpStream final : public redmiklab::TcpStream {
public:
    explicit FakeTcpStream(std::shared_ptr<TcpState> state) : state_(std::move(state)) {}
    redmiklab::SocketConnectResult connect_to(const redmiklab::IpAddress&, uint16_t) override { return redmiklab::SocketConnectResult::Connected; }
    redmiklab::SocketConnectResult finish_connect() override { return redmiklab::SocketConnectResult::Connected; }
    ssize_t send_bytes(const uint8_t* bytes, size_t length) override {
        state_->sent.insert(state_->sent.end(), bytes, bytes + length);
        return static_cast<ssize_t>(length);
    }
    ssize_t receive_bytes(uint8_t* bytes, size_t capacity) override {
        if (state_->incoming.empty()) return -1;
        const auto value = state_->incoming.front();
        state_->incoming.pop_front();
        if (capacity < value.size()) return -1;
        std::memcpy(bytes, value.data(), value.size());
        return static_cast<ssize_t>(value.size());
    }
    bool shutdown_write() override { return true; }
    int file_descriptor() const override { return 10; }
private:
    std::shared_ptr<TcpState> state_;
};

struct UdpState {
    std::vector<uint8_t> sent;
    std::deque<std::string> incoming;
};

class FakeUdpStream final : public redmiklab::UdpStream {
public:
    explicit FakeUdpStream(std::shared_ptr<UdpState> state) : state_(std::move(state)) {}
    bool connect_to(const redmiklab::IpAddress&, uint16_t) override { return true; }
    ssize_t send_datagram(const uint8_t* bytes, size_t length) override {
        state_->sent.assign(bytes, bytes + length);
        return static_cast<ssize_t>(length);
    }
    ssize_t receive_datagram(uint8_t* bytes, size_t capacity) override {
        if (state_->incoming.empty()) return -1;
        const auto value = state_->incoming.front();
        state_->incoming.pop_front();
        if (capacity < value.size()) return -1;
        std::memcpy(bytes, value.data(), value.size());
        return static_cast<ssize_t>(value.size());
    }
    int file_descriptor() const override { return 11; }
private:
    std::shared_ptr<UdpState> state_;
};

std::vector<uint8_t> tcp_packet(uint32_t sequence, uint32_t acknowledgement, uint8_t flags, const std::string& payload = {}) {
    std::vector<uint8_t> packet(40 + payload.size(), 0);
    packet[0] = 0x45;
    packet[2] = static_cast<uint8_t>(packet.size() >> 8);
    packet[3] = static_cast<uint8_t>(packet.size());
    packet[9] = 6;
    packet[12] = 10;
    packet[15] = 2;
    packet[16] = 1;
    packet[19] = 1;
    packet[20] = 0xc0;
    packet[22] = 0x01;
    packet[23] = 0xbb;
    packet[24] = static_cast<uint8_t>(sequence >> 24);
    packet[25] = static_cast<uint8_t>(sequence >> 16);
    packet[26] = static_cast<uint8_t>(sequence >> 8);
    packet[27] = static_cast<uint8_t>(sequence);
    packet[28] = static_cast<uint8_t>(acknowledgement >> 24);
    packet[29] = static_cast<uint8_t>(acknowledgement >> 16);
    packet[30] = static_cast<uint8_t>(acknowledgement >> 8);
    packet[31] = static_cast<uint8_t>(acknowledgement);
    packet[32] = 0x50;
    packet[33] = flags;
    std::copy(payload.begin(), payload.end(), packet.begin() + 40);
    return packet;
}

std::vector<uint8_t> udp_packet(const std::string& payload) {
    std::vector<uint8_t> packet(28 + payload.size(), 0);
    packet[0] = 0x45;
    packet[2] = static_cast<uint8_t>(packet.size() >> 8);
    packet[3] = static_cast<uint8_t>(packet.size());
    packet[9] = 17;
    packet[12] = 10;
    packet[15] = 2;
    packet[16] = 8;
    packet[19] = 8;
    packet[20] = 0xcf;
    packet[21] = 0x08;
    packet[23] = 53;
    const uint16_t udp_length = static_cast<uint16_t>(8 + payload.size());
    packet[24] = static_cast<uint8_t>(udp_length >> 8);
    packet[25] = static_cast<uint8_t>(udp_length);
    std::copy(payload.begin(), payload.end(), packet.begin() + 28);
    return packet;
}

std::vector<uint8_t> ipv6_udp_packet(const std::string& payload) {
    std::vector<uint8_t> packet(48 + payload.size(), 0);
    packet[0] = 0x60;
    const uint16_t payload_length = static_cast<uint16_t>(8 + payload.size());
    packet[4] = static_cast<uint8_t>(payload_length >> 8);
    packet[5] = static_cast<uint8_t>(payload_length);
    packet[6] = 17;
    packet[7] = 64;
    packet[8] = 0x20;
    packet[9] = 0x01;
    packet[23] = 1;
    packet[24] = 0x20;
    packet[25] = 0x01;
    packet[39] = 2;
    packet[40] = 0xcf;
    packet[41] = 0x09;
    packet[43] = 53;
    packet[44] = static_cast<uint8_t>(payload_length >> 8);
    packet[45] = static_cast<uint8_t>(payload_length);
    std::copy(payload.begin(), payload.end(), packet.begin() + 48);
    return packet;
}
}  // namespace

int main() {
    auto tcp = std::make_shared<TcpState>();
    auto udp = std::make_shared<UdpState>();
    std::vector<redmiklab::ForwardingObservation> observations;
    redmiklab::ForwarderEngine engine(
        [tcp] { return std::make_unique<FakeTcpStream>(tcp); },
        [udp] { return std::make_unique<FakeUdpStream>(udp); },
        [&](const redmiklab::ForwardingObservation& observation) { observations.push_back(observation); });

    assert(engine.on_tun_packet(tcp_packet(100, 0, 0x02), 0).size() == 1);
    assert(engine.on_tun_packet(tcp_packet(101, 501, 0x18, "hello"), 1).size() == 1);
    assert(std::string(tcp->sent.begin(), tcp->sent.end()) == "hello");
    tcp->incoming.push_back("world");
    assert(engine.poll(2).size() == 1);

    assert(engine.on_tun_packet(udp_packet("query"), 3).empty());
    assert(std::string(udp->sent.begin(), udp->sent.end()) == "query");
    udp->incoming.push_back("answer");
    assert(engine.poll(4).size() == 1);
    const auto has_stage = [&](redmiklab::ForwardingStage stage, redmiklab::ForwardingDirection direction) {
        return std::any_of(observations.begin(), observations.end(), [&](const auto& value) {
            return value.stage == stage && value.direction == direction && value.bytes > 0;
        });
    };
    assert(has_stage(redmiklab::ForwardingStage::TunObserved, redmiklab::ForwardingDirection::Upstream));
    assert(has_stage(redmiklab::ForwardingStage::UpstreamSocketAccepted, redmiklab::ForwardingDirection::Upstream));
    assert(has_stage(redmiklab::ForwardingStage::DownstreamSocketReceived, redmiklab::ForwardingDirection::Downstream));
    assert(engine.tcp_session_count() == 1);
    assert(engine.udp_session_count() == 1);
    assert(engine.on_tun_packet(ipv6_udp_packet("v6"), 5).empty());
    assert(engine.udp_session_count() == 2);
    assert(std::string(udp->sent.begin(), udp->sent.end()) == "v6");
    engine.poll(130000);
    assert(engine.udp_session_count() == 0);
}
