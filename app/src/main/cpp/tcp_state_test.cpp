#include "tcp_state.h"
#include <cassert>

int main() {
    redmiklab::TcpStateMachine state;
    assert(state.on_client_flags(0x02) == redmiklab::TcpAction::OpenDirectSocket);
    assert(state.state() == redmiklab::TcpState::Connecting);
    state.on_socket_connected();
    assert(state.state() == redmiklab::TcpState::Established);
    assert(state.on_client_flags(0x01) == redmiklab::TcpAction::CloseDirectSocket);
    assert(state.state() == redmiklab::TcpState::Closing);
}
