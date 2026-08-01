#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>

namespace redmiklab {
std::optional<std::string> extract_tls_sni(const uint8_t* payload, size_t length);
}
