#include "flow_table.h"
#include <cassert>

int main() {
    redmiklab::FlowTable table;
    const redmiklab::FlowKey key{6, 0x0a000001, 49152, 0x08080808, 443};

    assert(table.observe(key, 120) == redmiklab::FlowObservation::Created);
    assert(table.observe(key, 80) == redmiklab::FlowObservation::Existing);
    assert(table.bytes_for(key) == 200);
}
