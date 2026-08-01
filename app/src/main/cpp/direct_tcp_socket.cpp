#include "direct_tcp_socket.h"

#include <arpa/inet.h>
#include <cerrno>
#include <fcntl.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>

namespace redmiklab {

DirectTcpSocket::DirectTcpSocket(std::function<bool(int)> protect_socket)
    : protect_socket_(std::move(protect_socket)) {}

DirectTcpSocket::~DirectTcpSocket() {
    close_socket();
}

SocketConnectResult DirectTcpSocket::connect_to(const IpAddress& destination_address, uint16_t destination_port) {
    close_socket();
    if (!destination_address.valid()) return SocketConnectResult::Failed;
    const int family = destination_address.length == 16 ? AF_INET6 : AF_INET;
    file_descriptor_ = socket(family, SOCK_STREAM, IPPROTO_TCP);
    if (file_descriptor_ < 0 || !protect_socket_(file_descriptor_)) {
        close_socket();
        return SocketConnectResult::Failed;
    }
    const int flags = fcntl(file_descriptor_, F_GETFL, 0);
    if (flags < 0 || fcntl(file_descriptor_, F_SETFL, flags | O_NONBLOCK) < 0) {
        close_socket();
        return SocketConnectResult::Failed;
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
    const int result = connect(file_descriptor_, reinterpret_cast<sockaddr*>(&address), address_size);
    if (result == 0) return SocketConnectResult::Connected;
    if (errno == EINPROGRESS) return SocketConnectResult::Connecting;
    close_socket();
    return SocketConnectResult::Failed;
}

SocketConnectResult DirectTcpSocket::finish_connect() {
    if (file_descriptor_ < 0) return SocketConnectResult::Failed;
    int socket_error = 0;
    socklen_t length = sizeof(socket_error);
    if (getsockopt(file_descriptor_, SOL_SOCKET, SO_ERROR, &socket_error, &length) < 0) {
        close_socket();
        return SocketConnectResult::Failed;
    }
    if (socket_error == 0) return SocketConnectResult::Connected;
    if (socket_error == EINPROGRESS || socket_error == EALREADY) return SocketConnectResult::Connecting;
    close_socket();
    return SocketConnectResult::Failed;
}

ssize_t DirectTcpSocket::send_bytes(const uint8_t* bytes, size_t length) {
    if (file_descriptor_ < 0 || bytes == nullptr) return -1;
    return send(file_descriptor_, bytes, length, MSG_NOSIGNAL);
}

ssize_t DirectTcpSocket::receive_bytes(uint8_t* bytes, size_t capacity) {
    if (file_descriptor_ < 0 || bytes == nullptr) return -1;
    return recv(file_descriptor_, bytes, capacity, 0);
}

bool DirectTcpSocket::shutdown_write() {
    return file_descriptor_ >= 0 && shutdown(file_descriptor_, SHUT_WR) == 0;
}

void DirectTcpSocket::close_socket() {
    if (file_descriptor_ >= 0) ::close(file_descriptor_);
    file_descriptor_ = -1;
}

}  // namespace redmiklab
