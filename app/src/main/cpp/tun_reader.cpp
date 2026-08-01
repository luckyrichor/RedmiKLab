#include "tun_reader.h"

#include <array>
#include <unistd.h>

namespace redmiklab {

std::optional<ObservedPacket> TunReader::read_once() {
    std::array<uint8_t, 65535> buffer{};
    const ssize_t count = read(file_descriptor_, buffer.data(), buffer.size());
    if (count <= 0) return std::nullopt;
    const auto metadata = parse_ipv4_packet(buffer.data(), static_cast<size_t>(count));
    if (!metadata.has_value()) return std::nullopt;
    const uint8_t protocol = metadata->protocol == TransportProtocol::Tcp ? 6 :
        metadata->protocol == TransportProtocol::Udp ? 17 : 0;
    const FlowKey key{protocol, metadata->source_address, metadata->source_port,
        metadata->destination_address, metadata->destination_port};
    return ObservedPacket{*metadata, flows_.observe(key, metadata->wire_bytes)};
}

}  // namespace redmiklab
