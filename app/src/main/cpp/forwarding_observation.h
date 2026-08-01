#pragma once

#include "flow_table.h"

#include <cstdint>
#include <functional>
#include <string>

namespace redmiklab {

enum class ForwardingDirection { Upstream, Downstream };
enum class ForwardingStage { TunObserved, UpstreamSocketAccepted, DownstreamSocketReceived, Dropped };
enum class ForwardingOutcome { Observed, Accepted, Received, Failed };

struct ForwardingObservation {
    FlowKey key;
    ForwardingDirection direction;
    ForwardingStage stage;
    ForwardingOutcome outcome;
    uint64_t bytes;
    std::string reason;
    std::string hostname;
};

using SessionObserver = std::function<void(
    ForwardingDirection,
    ForwardingStage,
    ForwardingOutcome,
    uint64_t,
    const std::string&)>;

}  // namespace redmiklab
