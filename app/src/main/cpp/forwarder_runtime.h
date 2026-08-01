#pragma once

#include <atomic>
#include <functional>
#include <string>
#include <thread>

#include "flow_table.h"
#include "forwarding_observation.h"

namespace redmiklab {

class ForwarderRuntime {
public:
    ForwarderRuntime(
        int tunnel_file_descriptor,
        std::function<bool(int)> protect_socket,
        std::function<void(const ForwardingObservation&)> flow_observer = {});
    ~ForwarderRuntime();

    ForwarderRuntime(const ForwarderRuntime&) = delete;
    ForwarderRuntime& operator=(const ForwarderRuntime&) = delete;

    bool start();
    void stop();
    bool running() const { return running_.load(); }

private:
    void run_loop();

    int source_tunnel_file_descriptor_;
    int tunnel_file_descriptor_ = -1;
    std::function<bool(int)> protect_socket_;
    std::function<void(const ForwardingObservation&)> flow_observer_;
    std::atomic<bool> stop_requested_{false};
    std::atomic<bool> running_{false};
    std::thread worker_;
};

}  // namespace redmiklab
