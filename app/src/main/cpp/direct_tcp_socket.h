#pragma once

#include "ip_address.h"

#include <cstdint>
#include <functional>
#include <cstddef>
#include <sys/types.h>

namespace redmiklab {

enum class SocketConnectResult { Connected, Connecting, Failed };

class TcpStream {
public:
    virtual ~TcpStream() = default;
    virtual SocketConnectResult connect_to(const IpAddress& destination_address, uint16_t destination_port) = 0;
    virtual SocketConnectResult finish_connect() = 0;
    virtual ssize_t send_bytes(const uint8_t* bytes, size_t length) = 0;
    virtual ssize_t receive_bytes(uint8_t* bytes, size_t capacity) = 0;
    virtual bool shutdown_write() = 0;
    virtual int file_descriptor() const = 0;
};

class DirectTcpSocket : public TcpStream {
public:
    explicit DirectTcpSocket(std::function<bool(int)> protect_socket);
    ~DirectTcpSocket();

    DirectTcpSocket(const DirectTcpSocket&) = delete;
    DirectTcpSocket& operator=(const DirectTcpSocket&) = delete;

    SocketConnectResult connect_to(const IpAddress& destination_address, uint16_t destination_port) override;
    SocketConnectResult finish_connect() override;
    ssize_t send_bytes(const uint8_t* bytes, size_t length) override;
    ssize_t receive_bytes(uint8_t* bytes, size_t capacity) override;
    bool shutdown_write() override;
    int file_descriptor() const override { return file_descriptor_; }

private:
    void close_socket();

    std::function<bool(int)> protect_socket_;
    int file_descriptor_ = -1;
};

}  // namespace redmiklab
