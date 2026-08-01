package com.redmiklab.diagnostics

import com.redmiklab.model.NetworkSnapshot

fun interface NetworkSnapshotSource {
    fun read(): NetworkSnapshot
}
