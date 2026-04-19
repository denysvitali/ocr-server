package it.denv.ocr.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

data class ServerStatsSnapshot(
    val running: Boolean = false,
    val startedAt: Long = 0L,
    val ips: List<String> = emptyList(),
    val port: Int = 0,
    val processed: Long = 0L,
    val failed: Long = 0L,
    val inFlight: Int = 0,
    val totalBytes: Long = 0L,
    val totalLatencyMs: Long = 0L,
    val lastLatencyMs: Long = 0L,
) {
    val avgLatencyMs: Long
        get() = if (processed > 0) totalLatencyMs / processed else 0L
}

object ServerStats {
    private val _state = MutableStateFlow(ServerStatsSnapshot())
    val state: StateFlow<ServerStatsSnapshot> = _state.asStateFlow()

    private val inFlight = AtomicLong(0)

    fun markStarted(ips: List<String>, port: Int) {
        _state.update {
            it.copy(
                running = true,
                startedAt = System.currentTimeMillis(),
                ips = ips,
                port = port,
            )
        }
    }

    fun markStopped() {
        _state.update { it.copy(running = false) }
    }

    fun requestStarted(): Long {
        val n = inFlight.incrementAndGet()
        _state.update { it.copy(inFlight = n.toInt()) }
        return System.currentTimeMillis()
    }

    fun requestSucceeded(startMs: Long, bytes: Long) {
        val n = inFlight.decrementAndGet().coerceAtLeast(0)
        val latency = System.currentTimeMillis() - startMs
        _state.update {
            it.copy(
                inFlight = n.toInt(),
                processed = it.processed + 1,
                totalBytes = it.totalBytes + bytes,
                totalLatencyMs = it.totalLatencyMs + latency,
                lastLatencyMs = latency,
            )
        }
    }

    fun requestFailed() {
        val n = inFlight.decrementAndGet().coerceAtLeast(0)
        _state.update { it.copy(inFlight = n.toInt(), failed = it.failed + 1) }
    }
}
