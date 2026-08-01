#include "tls_sni_parser.h"

namespace redmiklab {
namespace {
uint16_t read_u16(const uint8_t* value) {
    return static_cast<uint16_t>((static_cast<uint16_t>(value[0]) << 8) | value[1]);
}
}

std::optional<std::string> extract_tls_sni(const uint8_t* payload, size_t length) {
    if (payload == nullptr || length < 9 || payload[0] != 22 || payload[5] != 1) return std::nullopt;
    const size_t record_end = 5 + read_u16(payload + 3);
    if (record_end > length) return std::nullopt;
    size_t cursor = 9;
    if (cursor + 34 > record_end) return std::nullopt;
    cursor += 34;
    if (cursor + 1 > record_end) return std::nullopt;
    const size_t session_length = payload[cursor++];
    if (cursor + session_length + 2 > record_end) return std::nullopt;
    cursor += session_length;
    const size_t cipher_length = read_u16(payload + cursor);
    cursor += 2;
    if (cursor + cipher_length + 1 > record_end) return std::nullopt;
    cursor += cipher_length;
    const size_t compression_length = payload[cursor++];
    if (cursor + compression_length + 2 > record_end) return std::nullopt;
    cursor += compression_length;
    const size_t extensions_length = read_u16(payload + cursor);
    cursor += 2;
    const size_t extensions_end = cursor + extensions_length;
    if (extensions_end > record_end) return std::nullopt;
    while (cursor + 4 <= extensions_end) {
        const uint16_t type = read_u16(payload + cursor);
        const size_t extension_length = read_u16(payload + cursor + 2);
        cursor += 4;
        if (cursor + extension_length > extensions_end) return std::nullopt;
        if (type == 0 && extension_length >= 5) {
            size_t name_cursor = cursor + 2;
            const size_t name_list_end = cursor + 2 + read_u16(payload + cursor);
            if (name_list_end > cursor + extension_length) return std::nullopt;
            while (name_cursor + 3 <= name_list_end) {
                const uint8_t name_type = payload[name_cursor++];
                const size_t name_length = read_u16(payload + name_cursor);
                name_cursor += 2;
                if (name_cursor + name_length > name_list_end) return std::nullopt;
                if (name_type == 0 && name_length > 0) {
                    return std::string(
                        reinterpret_cast<const char*>(payload + name_cursor),
                        name_length);
                }
                name_cursor += name_length;
            }
        }
        cursor += extension_length;
    }
    return std::nullopt;
}
}
