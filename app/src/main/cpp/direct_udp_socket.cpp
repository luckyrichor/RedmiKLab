#include "direct_udp_socket.h"

#include <arpa/inet.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

namespace redmiklab {

DirectUdpSocket::DirectUdpSocket(std::function<bool(int)> protect_socket)
    : protect_socket_(std::move(protect_socket)) {}

DirectUdpSocket::~DirectUdpSocket() {
    close_socket();
}

bool DirectUdpSocket::connect_to(const IpAddress& destination_address, uint16_t destination_port) {
    close_socket();
    if (!destination_address.valid()) return false;
    const int family = destination_address.length == 16 ? AF_INET6 : AF_INET;
    file_descriptor_ = socket(family, SOCK_DGRAM, IPPROTO_UDP);
    if (file_descriptor_ < 0 || !protect_socket_(file_descriptor_)) {
        close_socket();
        return false;
    }
    const int flags = fcntl(file_descriptor_, F_GETFL, 0);
    if (flags < 0 || fcntl(file_descriptor_, F_SETFL, flags | O_NONBLOCK) < 0) {
        close_socket();
        return false;
    }
    sockaddr_storage address{};
    socklen_t address_size = 0;
    if (family == AF_INET6) {
        auto* address6 = reinterpret_cast<sockaddr_in6*>(&address);
        address6->sin6_family = AF_INET6;
        std::copy(destination_address.bytes.begin(), destination_address.bytes.end(), address6->sin6_addr.s6_addr);
        address6->sin6_port = htons(destination_port);
        address_size = sizeof(sockaddr_in6);
    } else {
        auto* address4 = reinterpret_cast<sockaddr_in*>(&address);
        address4->sin_family = AF_INET;
        std::copy(destination_address.bytes.begin(), destination_address.bytes.begin() + 4,
            reinterpret_cast<uint8_t*>(&address4->sin_addr.s_addr));
        address4->sin_port = htons(destination_port);
        address_size = sizeof(sockaddr_in);
    }
    if (connect(file_descriptor_, reinterpret_cast<sockaddr*>(&address), address_size) < 0) {
        close_socket();
        return false;
    }
    return true;
}

ssize_t DirectUdpSocket::send_datagram(const uint8_t* bytes, size_t length) {
    if (file_descriptor_ < 0 || bytes == nullptr) return -1;
    return send(file_descriptor_, bytes, length, 0);
}

ssize_t DirectUdpSocket::receive_datagram(uint8_t* bytes, size_t capacity) {
    if (file_descriptor_ < 0 || bytes == nullptr) return -1;
    return recv(file_descriptor_, bytes, capacity, 0);
}

void DirectUdpSocket::close_socket() {
    if (file_descriptor_ >= 0) ::close(file_descriptor_);
    file_descriptor_ = -1;
}

}  // namespace redmiklab
