#include "checksum.h"

namespace redmiklab {

uint16_t internet_checksum(const uint8_t* bytes, size_t length) {
    uint32_t sum = 0;
    while (length > 1) {
        sum += static_cast<uint16_t>((static_cast<uint16_t>(bytes[0]) << 8) | bytes[1]);
        bytes += 2;
        length -= 2;
    }
    if (length == 1) sum += static_cast<uint16_t>(bytes[0] << 8);
    while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
    return static_cast<uint16_t>(~sum);
}

}  // namespace redmiklab
