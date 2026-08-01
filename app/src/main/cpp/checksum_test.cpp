#include "checksum.h"
#include <cassert>
#include <cstdint>

int main() {
    uint8_t header[] = {0x45, 0x00, 0x00, 0x54, 0x00, 0x00, 0x40, 0x00, 0x40, 0x01, 0x00, 0x00, 0xc0, 0xa8, 0x00, 0x01, 0x08, 0x08, 0x08, 0x08};
    const auto checksum = redmiklab::internet_checksum(header, sizeof(header));
    header[10] = static_cast<uint8_t>(checksum >> 8);
    header[11] = static_cast<uint8_t>(checksum & 0xff);
    assert(redmiklab::internet_checksum(header, sizeof(header)) == 0);
}
