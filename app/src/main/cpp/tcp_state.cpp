#include "tcp_state.h"

namespace redmiklab {
namespace {
constexpr uint8_t kSyn = 0x02;
constexpr uint8_t kFin = 0x01;
constexpr uint8_t kRst = 0x04;
}

TcpAction TcpStateMachine::on_client_flags(uint8_t flags) {
    if ((flags & kRst) != 0) {
        const bool needs_close = state_ == TcpState::Connecting || state_ == TcpState::Established;
        state_ = TcpState::Closed;
        return needs_close ? TcpAction::CloseDirectSocket : TcpAction::Ignore;
    }
    if (state_ == TcpState::New && (flags & kSyn) != 0) {
        state_ = TcpState::Connecting;
        return TcpAction::OpenDirectSocket;
    }
    if (state_ == TcpState::Established && (flags & kFin) != 0) {
        state_ = TcpState::Closing;
        return TcpAction::CloseDirectSocket;
    }
    return TcpAction::Ignore;
}

void TcpStateMachine::on_socket_connected() {
    if (state_ == TcpState::Connecting) state_ = TcpState::Established;
}

void TcpStateMachine::on_socket_failure() {
    state_ = TcpState::Closed;
}

}  // namespace redmiklab
