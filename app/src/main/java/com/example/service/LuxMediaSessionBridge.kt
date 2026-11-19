package com.example.service

import android.media.session.MediaController
import com.example.system.MediaPlaybackState
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LuxMediaSessionBridge {
    private val _mediaState = MutableStateFlow(MediaPlaybackState())
    val mediaState: StateFlow<MediaPlaybackState> = _mediaState.asStateFlow()

    @Volatile
    private var activeControllerRef: WeakReference<MediaController>? = null

    fun updateState(state: MediaPlaybackState, controller: MediaController?) {
        activeControllerRef = controller?.let { WeakReference(it) }
        _mediaState.value = state
    }

    fun updateAccessState(hasAccess: Boolean) {
        if (!hasAccess) {
            activeControllerRef = null
            _mediaState.value = MediaPlaybackState(
                hasNotificationAccess = false,
                isPlaying = false,
                hasActiveSession = false,
                title = "Media Bridge Inactive",
                artist = "Grant Notification Access for live track metadata"
            )
        } else if (!_mediaState.value.hasNotificationAccess) {
            _mediaState.value = _mediaState.value.copy(
                hasNotificationAccess = true,
                title = "No Active Media",
                artist = "Start playback in any media app"
            )
        }
    }

    fun dispatchPlayPause(): Boolean {
        val controller = activeControllerRef?.get() ?: return false
        val isPlaying = _mediaState.value.isPlaying
        return runCatching {
            if (isPlaying) controller.transportControls.pause()
            else controller.transportControls.play()
            true
        }.getOrDefault(false)
    }

    fun dispatchNext(): Boolean {
        val controller = activeControllerRef?.get() ?: return false
        return runCatching {
            controller.transportControls.skipToNext()
            true
        }.getOrDefault(false)
    }

    fun dispatchPrevious(): Boolean {
        val controller = activeControllerRef?.get() ?: return false
        return runCatching {
            controller.transportControls.skipToPrevious()
            true
        }.getOrDefault(false)
    }
}
