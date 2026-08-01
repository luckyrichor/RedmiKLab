#include "direct_udp_socket.h"

#include <arpa/inet.h>
#include <array>
#include <cassert>
#include <chrono>
#include <netinet/in.h>
#include <thread>
#include <unistd.h>

int main() {
    const int server = socket(AF_INET, SOCK_DGRAM, 0);
    assert(server >= 0);
    sockaddr_in address{};
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    assert(bind(server, reinterpret_cast<sockaddr*>(&address), sizeof(address)) == 0);
    socklen_t size = sizeof(address);
    assert(getsockname(server, reinterpret_cast<sockaddr*>(&address), &size) == 0);

    bool protected_socket = false;
    redmiklab::DirectUdpSocket client([&](int) { protected_socket = true; return true; });
    assert(client.connect_to(redmiklab::IpAddress::ipv4(0x7f000001), ntohs(address.sin_port)));
    assert(protected_socket);
    const uint8_t request[] = {'p', 'i', 'n', 'g'};
    assert(client.send_datagram(request, sizeof(request)) == 4);

    std::array<uint8_t, 4> received{};
    sockaddr_in client_address{};
    socklen_t client_size = sizeof(client_address);
    assert(recvfrom(server, received.data(), received.size(), 0,
        reinterpret_cast<sockaddr*>(&client_address), &client_size) == 4);
    const uint8_t response[] = {'p', 'o', 'n', 'g'};
    assert(sendto(server, response, sizeof(response), 0,
        reinterpret_cast<sockaddr*>(&client_address), client_size) == 4);

    ssize_t response_size = -1;
    for (int attempt = 0; response_size < 0 && attempt < 20; ++attempt) {
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        response_size = client.receive_datagram(received.data(), received.size());
    }
    assert(response_size == 4);
    assert(received[1] == 'o');
    close(server);

    const int server6 = socket(AF_INET6, SOCK_DGRAM, 0);
    assert(server6 >= 0);
    sockaddr_in6 address6{};
    address6.sin6_family = AF_INET6;
    address6.sin6_addr = in6addr_loopback;
    assert(bind(server6, reinterpret_cast<sockaddr*>(&address6), sizeof(address6)) == 0);
    socklen_t size6 = sizeof(address6);
    assert(getsockname(server6, reinterpret_cast<sockaddr*>(&address6), &size6) == 0);
    redmiklab::DirectUdpSocket client6([](int) { return true; });
    assert(client6.connect_to(redmiklab::IpAddress::ipv6(address6.sin6_addr.s6_addr), ntohs(address6.sin6_port)));
    assert(client6.send_datagram(request, sizeof(request)) == 4);
    assert(recvfrom(server6, received.data(), received.size(), 0, nullptr, nullptr) == 4);
    close(server6);
}
