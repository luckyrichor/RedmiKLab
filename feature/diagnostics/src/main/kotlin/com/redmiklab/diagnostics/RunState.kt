package com.redmiklab.diagnostics

sealed interface RunState {
    data class Running(val budgetExhausted: Boolean) : RunState {
        fun next(event: RunEvent): RunAction = when (event) {
            RunEvent.ThroughputTick -> if (budgetExhausted) RunAction.SkipThroughputKeepSnapshot else RunAction.RunThroughput
            RunEvent.SnapshotTick -> RunAction.CollectSnapshot
            RunEvent.End -> RunAction.StopCompleted
            RunEvent.PermissionRevoked -> RunAction.StopPermissionRevoked
        }
    }
}

enum class RunEvent { SnapshotTick, ThroughputTick, End, PermissionRevoked }
enum class RunAction { CollectSnapshot, RunThroughput, SkipThroughputKeepSnapshot, StopCompleted, StopPermissionRevoked }
