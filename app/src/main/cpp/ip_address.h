#pragma once

#include <algorithm>
#include <array>
#include <cstdint>

namespace redmiklab {

struct IpAddress {
    uint8_t length = 0;
    std::array<uint8_t, 16> bytes{};

    static IpAddress ipv4(uint32_t value) {
        IpAddress address;
        address.length = 4;
        address.bytes[0] = static_cast<uint8_t>(value >> 24);
        address.bytes[1] = static_cast<uint8_t>(value >> 16);
        address.bytes[2] = static_cast<uint8_t>(value >> 8);
        address.bytes[3] = static_cast<uint8_t>(value);
        return address;
    }

    static IpAddress ipv6(const uint8_t* value) {
        IpAddress address;
        if (value == nullptr) return address;
        address.length = 16;
        std::copy(value, value + 16, address.bytes.begin());
        return address;
    }

    bool valid() const { return length == 4 || length == 16; }

    bool operator==(const IpAddress& other) const {
        return length == other.length && bytes == other.bytes;
    }
};

}  // namespace redmiklab
