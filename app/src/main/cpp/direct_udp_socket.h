#pragma once

#include "ip_address.h"

#include <cstddef>
#include <cstdint>
#include <functional>
#include <sys/types.h>

namespace redmiklab {

class UdpStream {
public:
    virtual ~UdpStream() = default;
    virtual bool connect_to(const IpAddress& destination_address, uint16_t destination_port) = 0;
    virtual ssize_t send_datagram(const uint8_t* bytes, size_t length) = 0;
    virtual ssize_t receive_datagram(uint8_t* bytes, size_t capacity) = 0;
    virtual int file_descriptor() const = 0;
};

class DirectUdpSocket final : public UdpStream {
public:
    explicit DirectUdpSocket(std::function<bool(int)> protect_socket);
    ~DirectUdpSocket() override;

    DirectUdpSocket(const DirectUdpSocket&) = delete;
    DirectUdpSocket& operator=(const DirectUdpSocket&) = delete;

    bool connect_to(const IpAddress& destination_address, uint16_t destination_port) override;
    ssize_t send_datagram(const uint8_t* bytes, size_t length) override;
    ssize_t receive_datagram(uint8_t* bytes, size_t capacity) override;
    int file_descriptor() const override { return file_descriptor_; }

private:
    void close_socket();

    std::function<bool(int)> protect_socket_;
    int file_descriptor_ = -1;
};

}  // namespace redmiklab
