package com.example.beesmart.data.repository

import com.example.beesmart.network.BackendReachability
import com.example.beesmart.sync.ConnectivityObserver

/**
 * Single source of truth for the repositories' online check: the backend is
 * reachable only when the device is online and the backend has not recently
 * been flagged as unreachable.
 */
internal fun ConnectivityObserver.canReachBackend(backendReachability: BackendReachability): Boolean =
    isCurrentlyOnline() && !backendReachability.isLikelyUnreachable()
