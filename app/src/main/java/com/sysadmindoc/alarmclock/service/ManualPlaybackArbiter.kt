package com.sysadmindoc.alarmclock.service

import javax.inject.Inject
import javax.inject.Singleton

enum class ManualAudioOwner {
    NONE,
    HISTORY_REPLAY,
    CATALOG_PREVIEW
}

/**
 * Coordinates single-owner manual playback between history replay and catalog preview.
 * Synchronously preempts any previous owner on [claim] and issues monotonic tokens so
 * delayed async callbacks from an older request cannot overwrite or hijack the active owner.
 */
@Singleton
class ManualPlaybackArbiter @Inject constructor() {

    private val lock = Any()
    private var currentOwner: ManualAudioOwner = ManualAudioOwner.NONE
    private var currentToken: Long = 0L
    private val stopHandlers = mutableMapOf<ManualAudioOwner, () -> Unit>()

    fun registerStopHandler(owner: ManualAudioOwner, onPreempted: () -> Unit) {
        if (owner == ManualAudioOwner.NONE) return
        synchronized(lock) {
            stopHandlers[owner] = onPreempted
        }
    }

    fun claim(owner: ManualAudioOwner): Long {
        require(owner != ManualAudioOwner.NONE) { "Cannot claim ownership for NONE" }
        var handlerToStop: (() -> Unit)? = null
        val token: Long
        synchronized(lock) {
            if (currentOwner == owner) {
                return currentToken
            }
            val previousOwner = currentOwner
            token = ++currentToken
            currentOwner = owner
            if (previousOwner != ManualAudioOwner.NONE) {
                handlerToStop = stopHandlers[previousOwner]
            }
        }
        handlerToStop?.invoke()
        return token
    }

    fun isCurrent(owner: ManualAudioOwner, token: Long): Boolean =
        synchronized(lock) {
            currentOwner == owner && currentToken == token
        }

    fun release(owner: ManualAudioOwner, token: Long? = null) {
        if (owner == ManualAudioOwner.NONE) return
        synchronized(lock) {
            if (currentOwner == owner && (token == null || currentToken == token)) {
                currentOwner = ManualAudioOwner.NONE
                currentToken += 1
            }
        }
    }

    fun activeOwner(): ManualAudioOwner = synchronized(lock) { currentOwner }
}
