#pragma once

#include <array>
#include <cstdint>
#include <unordered_map>

namespace redmiklab {

struct FlowKey {
    uint8_t protocol;
    uint32_t source_address;
    uint16_t source_port;
    uint32_t destination_address;
    uint16_t destination_port;
    uint8_t address_length;
    std::array<uint8_t, 16> source_ip;
    std::array<uint8_t, 16> destination_ip;

    bool operator==(const FlowKey& other) const {
        if (protocol != other.protocol ||
            source_port != other.source_port ||
            destination_port != other.destination_port) return false;
        if (address_length == 16 || other.address_length == 16) {
            return address_length == other.address_length &&
                source_ip == other.source_ip && destination_ip == other.destination_ip;
        }
        return
            source_address == other.source_address &&
            destination_address == other.destination_address;
    }
};

struct FlowKeyHash {
    size_t operator()(const FlowKey& key) const;
};

enum class FlowObservation { Created, Existing };

class FlowTable {
public:
    FlowObservation observe(const FlowKey& key, uint64_t bytes);
    uint64_t bytes_for(const FlowKey& key) const;

private:
    std::unordered_map<FlowKey, uint64_t, FlowKeyHash> flows_;
};

}  // namespace redmiklab
