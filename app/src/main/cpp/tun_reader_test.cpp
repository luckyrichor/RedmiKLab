#include "tun_reader.h"
#include <cassert>
#include <cstdint>
#include <unistd.h>
#include <vector>

int main() {
    int fds[2];
    assert(pipe(fds) == 0);
    const std::vector<uint8_t> packet = {
        0x45, 0x00, 0x00, 0x28, 0x00, 0x00, 0x40, 0x00, 0x40, 0x06, 0x00, 0x00,
        10, 0, 0, 1, 8, 8, 8, 8, 0xc0, 0x00, 0x01, 0xbb,
        0, 0, 0, 0, 0, 0, 0, 0, 0x50, 0x02, 0x20, 0x00, 0, 0, 0, 0,
    };
    assert(write(fds[1], packet.data(), packet.size()) == static_cast<ssize_t>(packet.size()));
    redmiklab::TunReader reader(fds[0]);
    const auto observed = reader.read_once();
    assert(observed.has_value());
    assert(observed->metadata.destination_port == 443);
    assert(observed->flow_observation == redmiklab::FlowObservation::Created);
    close(fds[0]);
    close(fds[1]);
}
