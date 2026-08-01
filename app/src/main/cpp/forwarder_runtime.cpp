#include "forwarder_runtime.h"

#include "direct_tcp_socket.h"
#include "direct_udp_socket.h"
#include "forwarder_engine.h"

#include <array>
#include <chrono>
#include <fcntl.h>
#include <poll.h>
#include <unistd.h>
#include <vector>

namespace redmiklab {
namespace {
uint64_t monotonic_millis() {
    return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count());
}
}

ForwarderRuntime::ForwarderRuntime(
        int tunnel_file_descriptor,
        std::function<bool(int)> protect_socket,
        std::function<void(const ForwardingObservation&)> flow_observer)
    : source_tunnel_file_descriptor_(tunnel_file_descriptor),
      protect_socket_(std::move(protect_socket)),
      flow_observer_(std::move(flow_observer)) {}

ForwarderRuntime::~ForwarderRuntime() {
    stop();
}

bool ForwarderRuntime::start() {
    if (running_.load() || source_tunnel_file_descriptor_ < 0 || !protect_socket_) return false;
    tunnel_file_descriptor_ = dup(source_tunnel_file_descriptor_);
    if (tunnel_file_descriptor_ < 0) return false;
    const int flags = fcntl(tunnel_file_descriptor_, F_GETFL, 0);
    if (flags < 0 || fcntl(tunnel_file_descriptor_, F_SETFL, flags | O_NONBLOCK) < 0) {
        close(tunnel_file_descriptor_);
        tunnel_file_descriptor_ = -1;
        return false;
    }
    stop_requested_.store(false);
    running_.store(true);
    worker_ = std::thread(&ForwarderRuntime::run_loop, this);
    return true;
}

void ForwarderRuntime::stop() {
    stop_requested_.store(true);
    if (worker_.joinable()) worker_.join();
    if (tunnel_file_descriptor_ >= 0) close(tunnel_file_descriptor_);
    tunnel_file_descriptor_ = -1;
    running_.store(false);
}

void ForwarderRuntime::run_loop() {
    ForwarderEngine engine(
        [this] { return std::make_unique<DirectTcpSocket>(protect_socket_); },
        [this] { return std::make_unique<DirectUdpSocket>(protect_socket_); },
        flow_observer_);
    std::array<uint8_t, 65535> buffer{};
    while (!stop_requested_.load()) {
        pollfd descriptor{tunnel_file_descriptor_, POLLIN, 0};
        const int ready = ::poll(&descriptor, 1, 10);
        const uint64_t now = monotonic_millis();
        if (ready > 0 && (descriptor.revents & POLLIN) != 0) {
            while (true) {
                const ssize_t count = read(tunnel_file_descriptor_, buffer.data(), buffer.size());
                if (count <= 0) break;
                std::vector<uint8_t> packet(buffer.begin(), buffer.begin() + count);
                const auto responses = engine.on_tun_packet(packet, now);
                for (const auto& response : responses) {
                    write(tunnel_file_descriptor_, response.data(), response.size());
                }
            }
        }
        const auto responses = engine.poll(now);
        for (const auto& response : responses) {
            write(tunnel_file_descriptor_, response.data(), response.size());
        }
    }
    running_.store(false);
}

}  // namespace redmiklab
