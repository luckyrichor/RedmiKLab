#include "flow_table.h"

namespace redmiklab {

size_t FlowKeyHash::operator()(const FlowKey& key) const {
    size_t value = key.protocol;
    if (key.address_length == 16) {
        for (const auto byte : key.source_ip) value = value * 31 + byte;
    } else {
        value = value * 31 + key.source_address;
    }
    value = value * 31 + key.source_port;
    if (key.address_length == 16) {
        for (const auto byte : key.destination_ip) value = value * 31 + byte;
    } else {
        value = value * 31 + key.destination_address;
    }
    return value * 31 + key.destination_port;
}

FlowObservation FlowTable::observe(const FlowKey& key, uint64_t bytes) {
    const auto [iterator, inserted] = flows_.try_emplace(key, 0);
    iterator->second += bytes;
    return inserted ? FlowObservation::Created : FlowObservation::Existing;
}

uint64_t FlowTable::bytes_for(const FlowKey& key) const {
    const auto iterator = flows_.find(key);
    return iterator == flows_.end() ? 0 : iterator->second;
}

}  // namespace redmiklab
