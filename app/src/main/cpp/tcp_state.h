#pragma once

#include <cstdint>

namespace redmiklab {

enum class TcpState { New, Connecting, Established, Closing, Closed };
enum class TcpAction { Ignore, OpenDirectSocket, CloseDirectSocket };

class TcpStateMachine {
public:
    TcpAction on_client_flags(uint8_t flags);
    void on_socket_connected();
    void on_socket_failure();
    TcpState state() const { return state_; }

private:
    TcpState state_ = TcpState::New;
};

}  // namespace redmiklab
