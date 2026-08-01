#pragma once

#include <cstddef>
#include <cstdint>

namespace redmiklab {
uint16_t internet_checksum(const uint8_t* bytes, size_t length);
}
