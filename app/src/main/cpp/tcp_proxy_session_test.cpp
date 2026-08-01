#include "tcp_proxy_session.h"

#include <cassert>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

namespace {

class FakeTcpStream final : public redmiklab::TcpStream {
public:
    redmiklab::SocketConnectResult connect_to(const redmiklab::IpAddress& address, uint16_t port) override {
        connected_address = address;
        connected_port = port;
        return redmiklab::SocketConnectResult::Connected;
    }
    redmiklab::SocketConnectResult finish_connect() override {
        return redmiklab::SocketConnectResult::Connected;
    }
    ssize_t send_bytes(const uint8_t* bytes, size_t length) override {
        const size_t accepted = std::min(length, maximum_send_bytes);
        sent.insert(sent.end(), bytes, bytes + accepted);
        return static_cast<ssize_t>(accepted);
    }
    ssize_t receive_bytes(uint8_t* bytes, size_t capacity) override {
        if (received_once) return eof_after_response ? 0 : -1;
        if (capacity < response.size()) return -1;
        std::memcpy(bytes, response.data(), response.size());
        received_once = true;
        return static_cast<ssize_t>(response.size());
    }
    bool shutdown_write() override {
        write_shutdown = true;
        return true;
    }
    int file_descriptor() const override { return 7; }

    redmiklab::IpAddress connected_address;
    uint16_t connected_port = 0;
    std::vector<uint8_t> sent;
    std::string response = "pong";
    bool received_once = false;
    bool write_shutdown = false;
    size_t maximum_send_bytes = static_cast<size_t>(-1);
    bool eof_after_response = false;
};

std::vector<uint8_t> client_packet(uint32_t sequence, uint32_t acknowledgement, uint8_t flags, const std::string& payload = {}) {
    std::vector<uint8_t> packet(40 + payload.size(), 0);
    packet[0] = 0x45;
    packet[2] = static_cast<uint8_t>(packet.size() >> 8);
    packet[3] = static_cast<uint8_t>(packet.size());
    packet[8] = 64;
    packet[9] = 6;
    packet[12] = 10;
    packet[15] = 2;
    packet[16] = 1;
    packet[17] = 1;
    packet[18] = 1;
    packet[19] = 1;
    packet[20] = 0xc0;
    packet[21] = 0x00;
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

}  // namespace

int main() {
    auto fake = std::make_unique<FakeTcpStream>();
    auto* stream = fake.get();
    redmiklab::TcpProxySession session(std::move(fake), 500);

    const auto syn_responses = session.on_client_packet(client_packet(100, 0, 0x02));
    assert(syn_responses.size() == 1);
    assert(syn_responses[0][33] == 0x12);
    assert(stream->connected_address == redmiklab::IpAddress::ipv4(0x01010101));
    assert(stream->connected_port == 443);
    const auto repeated_syn = session.on_client_packet(client_packet(100, 0, 0x02));
    assert(repeated_syn.size() == 1);
    assert(repeated_syn[0] == syn_responses[0]);

    const auto data_responses = session.on_client_packet(client_packet(101, 501, 0x18, "ping"));
    assert(data_responses.size() == 1);
    assert(std::string(stream->sent.begin(), stream->sent.end()) == "ping");
    assert(data_responses[0][33] == 0x10);

    const auto remote_responses = session.poll(100);
    assert(remote_responses.size() == 1);
    assert(std::string(remote_responses[0].begin() + 40, remote_responses[0].end()) == "pong");
    const auto retransmitted = session.poll(1200);
    assert(retransmitted.size() == 1);
    assert(retransmitted[0] == remote_responses[0]);
    assert(session.on_client_packet(client_packet(105, 505, 0x10)).empty());
    assert(session.poll(2300).empty());

    stream->eof_after_response = true;
    const auto remote_fin = session.poll(2400);
    assert(remote_fin.size() == 1);
    assert(remote_fin[0][33] == 0x11);
    assert(session.on_client_packet(client_packet(105, 506, 0x10)).empty());
    const auto client_fin_ack = session.on_client_packet(client_packet(105, 506, 0x11));
    assert(client_fin_ack.size() == 1);
    assert(stream->write_shutdown);
    assert(session.closed());

    auto partial_fake = std::make_unique<FakeTcpStream>();
    auto* partial_stream = partial_fake.get();
    partial_stream->maximum_send_bytes = 2;
    redmiklab::TcpProxySession partial_session(std::move(partial_fake), 700);
    assert(partial_session.on_client_packet(client_packet(200, 0, 0x02)).size() == 1);
    const auto first_partial_ack = partial_session.on_client_packet(client_packet(201, 701, 0x18, "ping"));
    assert(first_partial_ack.size() == 1);
    assert(partial_stream->sent.size() == 2);
    const auto retry_ack = partial_session.on_client_packet(client_packet(201, 701, 0x18, "ping"));
    assert(retry_ack.size() == 1);
    assert(std::string(partial_stream->sent.begin(), partial_stream->sent.end()) == "ping");
    const auto duplicate_ack = partial_session.on_client_packet(client_packet(201, 701, 0x18, "ping"));
    assert(duplicate_ack.size() == 1);
    assert(partial_stream->sent.size() == 4);

    auto timeout_fake = std::make_unique<FakeTcpStream>();
    redmiklab::TcpProxySession timeout_session(std::move(timeout_fake), 900);
    assert(timeout_session.on_client_packet(client_packet(300, 0, 0x02)).size() == 1);
    assert(timeout_session.poll(100).size() == 1);
    for (uint64_t now = 1100; now <= 5100; now += 1000) {
        assert(timeout_session.poll(now).size() == 1);
    }
    const auto timeout_reset = timeout_session.poll(6100);
    assert(timeout_reset.size() == 1);
    assert(timeout_reset[0][33] == 0x14);
    assert(timeout_session.closed());
}
