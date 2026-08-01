#include "direct_tcp_socket.h"
#include <arpa/inet.h>
#include <cassert>
#include <chrono>
#include <netinet/in.h>
#include <thread>
#include <unistd.h>
#include <array>

int main() {
    const int server = socket(AF_INET, SOCK_STREAM, 0);
    assert(server >= 0);
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    assert(bind(server, reinterpret_cast<sockaddr*>(&address), sizeof(address)) == 0);
    assert(listen(server, 1) == 0);
    socklen_t size = sizeof(address);
    assert(getsockname(server, reinterpret_cast<sockaddr*>(&address), &size) == 0);

    bool protected_socket = false;
    redmiklab::DirectTcpSocket client([&](int) { protected_socket = true; return true; });
    auto result = client.connect_to(redmiklab::IpAddress::ipv4(0x7f000001), ntohs(address.sin_port));
    for (int attempt = 0; result == redmiklab::SocketConnectResult::Connecting && attempt < 20; ++attempt) {
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        result = client.finish_connect();
    }
    assert(result == redmiklab::SocketConnectResult::Connected);
    assert(protected_socket);
    const int accepted = accept(server, nullptr, nullptr);
    assert(accepted >= 0);
    const uint8_t request[] = {'p', 'i', 'n', 'g'};
    assert(client.send_bytes(request, sizeof(request)) == 4);
    std::array<uint8_t, 4> received{};
    assert(read(accepted, received.data(), received.size()) == 4);
    assert(received[0] == 'p' && received[3] == 'g');
    const uint8_t response[] = {'p', 'o', 'n', 'g'};
    assert(write(accepted, response, sizeof(response)) == 4);
    ssize_t response_size = -1;
    for (int attempt = 0; response_size < 0 && attempt < 20; ++attempt) {
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        response_size = client.receive_bytes(received.data(), received.size());
    }
    assert(response_size == 4);
    assert(received[1] == 'o');
    close(accepted);
    close(server);

    const int server6 = socket(AF_INET6, SOCK_STREAM, 0);
    assert(server6 >= 0);
    sockaddr_in6 address6{};
    address6.sin6_family = AF_INET6;
    address6.sin6_addr = in6addr_loopback;
    assert(bind(server6, reinterpret_cast<sockaddr*>(&address6), sizeof(address6)) == 0);
    assert(listen(server6, 1) == 0);
    socklen_t size6 = sizeof(address6);
    assert(getsockname(server6, reinterpret_cast<sockaddr*>(&address6), &size6) == 0);
    redmiklab::DirectTcpSocket client6([](int) { return true; });
    auto result6 = client6.connect_to(redmiklab::IpAddress::ipv6(address6.sin6_addr.s6_addr), ntohs(address6.sin6_port));
    for (int attempt = 0; result6 == redmiklab::SocketConnectResult::Connecting && attempt < 20; ++attempt) {
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        result6 = client6.finish_connect();
    }
    assert(result6 == redmiklab::SocketConnectResult::Connected);
    const int accepted6 = accept(server6, nullptr, nullptr);
    assert(accepted6 >= 0);
    close(accepted6);
    close(server6);
}
