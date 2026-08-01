#pragma once

#include "flow_table.h"
#include "packet_parser.h"
#include <optional>

namespace redmiklab {

struct ObservedPacket {
    PacketMetadata metadata;
    FlowObservation flow_observation;
};

class TunReader {
public:
    explicit TunReader(int file_descriptor) : file_descriptor_(file_descriptor) {}
    std::optional<ObservedPacket> read_once();

private:
    int file_descriptor_;
    FlowTable flows_;
};

}  // namespace redmiklab
