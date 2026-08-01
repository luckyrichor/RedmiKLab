#include "tls_sni_parser.h"

#include <cassert>
#include <cstdint>
#include <string>
#include <vector>

namespace {
void append_u16(std::vector<uint8_t>& bytes, size_t value) {
    bytes.push_back(static_cast<uint8_t>(value >> 8));
    bytes.push_back(static_cast<uint8_t>(value));
}

std::vector<uint8_t> client_hello(const std::string& hostname) {
    std::vector<uint8_t> body{0x03, 0x03};
    body.insert(body.end(), 32, 0);
    body.push_back(0);
    append_u16(body, 2);
    body.push_back(0x13);
    body.push_back(0x01);
    body.push_back(1);
    body.push_back(0);

    std::vector<uint8_t> server_name;
    append_u16(server_name, 3 + hostname.size());
    server_name.push_back(0);
    append_u16(server_name, hostname.size());
    server_name.insert(server_name.end(), hostname.begin(), hostname.end());

    std::vector<uint8_t> extensions;
    append_u16(extensions, 0);
    append_u16(extensions, server_name.size());
    extensions.insert(extensions.end(), server_name.begin(), server_name.end());
    append_u16(body, extensions.size());
    body.insert(body.end(), extensions.begin(), extensions.end());

    std::vector<uint8_t> handshake{1};
    handshake.push_back(static_cast<uint8_t>(body.size() >> 16));
    handshake.push_back(static_cast<uint8_t>(body.size() >> 8));
    handshake.push_back(static_cast<uint8_t>(body.size()));
    handshake.insert(handshake.end(), body.begin(), body.end());

    std::vector<uint8_t> record{22, 0x03, 0x01};
    append_u16(record, handshake.size());
    record.insert(record.end(), handshake.begin(), handshake.end());
    return record;
}
}

int main() {
    const auto hello = client_hello("v3-dy-o.zjcdn.com");
    const auto hostname = redmiklab::extract_tls_sni(hello.data(), hello.size());
    assert(hostname.has_value());
    assert(*hostname == "v3-dy-o.zjcdn.com");

    const std::vector<uint8_t> not_tls{1, 2, 3};
    assert(!redmiklab::extract_tls_sni(not_tls.data(), not_tls.size()).has_value());
}
