package com.example.beesmart.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared circuit-breaker state for the backend. When a request fails with
 * an IOException, [markUnreachable] records the time; for the next
 * [COOLDOWN_MS] any request short-circuits via [ReachabilityInterceptor]
 * so repositories can fall through to Room without waiting on socket
 * timeouts.
 */
@Singleton
class BackendReachability @Inject constructor() {

    @Volatile private var unreachableUntilMs: Long = 0L

    private val _isReachable = MutableStateFlow(true)
    val isReachable: StateFlow<Boolean> = _isReachable.asStateFlow()

    fun markUnreachable() {
        unreachableUntilMs = System.currentTimeMillis() + COOLDOWN_MS
        _isReachable.value = false
    }

    fun markReachable() {
        unreachableUntilMs = 0L
        _isReachable.value = true
    }

    fun isLikelyUnreachable(): Boolean =
        System.currentTimeMillis() < unreachableUntilMs

    companion object {
        private const val COOLDOWN_MS = 30_000L
    }
}
