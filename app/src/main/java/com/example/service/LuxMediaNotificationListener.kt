package com.example.service

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import com.example.system.MediaPlaybackState

/**
 * Real NotificationListenerService bridging live system MediaSessions
 * (Spotify, YouTube Music, Chrome, System players, etc.) to Lux Control Center.
 */
class LuxMediaNotificationListener : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishCurrentController()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishCurrentController()
        }

        override fun onSessionDestroyed() {
            refreshActiveSessions()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bindBestController(controllers.orEmpty())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        LuxMediaSessionBridge.updateAccessState(true)
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val component = ComponentName(this, LuxMediaNotificationListener::class.java)
        runCatching {
            mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                component
            )
        }
        refreshActiveSessions()
    }

    override fun onListenerDisconnected() {
        cleanupListeners()
        LuxMediaSessionBridge.updateAccessState(false)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cleanupListeners()
        super.onDestroy()
    }

    private fun refreshActiveSessions() {
        val component = ComponentName(this, LuxMediaNotificationListener::class.java)
        val controllers = runCatching {
            mediaSessionManager?.getActiveSessions(component).orEmpty()
        }.getOrDefault(emptyList())
        bindBestController(controllers)
    }

    private fun bindBestController(controllers: List<MediaController>) {
        activeController?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        val chosen = controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: controllers.firstOrNull()

        activeController = chosen
        chosen?.let { runCatching { it.registerCallback(controllerCallback) } }
        publishCurrentController()
    }

    private fun publishCurrentController() {
        val controller = activeController
        if (controller == null) {
            LuxMediaSessionBridge.updateState(
                MediaPlaybackState(
                    hasNotificationAccess = true,
                    isPlaying = false,
                    hasActiveSession = false,
                    title = "No Active Media",
                    artist = "Start playback in any media app"
                ),
                null
            )
            return
        }

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Playing Media"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?.takeIf { it.isNotBlank() }
            ?: controller.packageName
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        LuxMediaSessionBridge.updateState(
            MediaPlaybackState(
                hasNotificationAccess = true,
                isPlaying = isPlaying,
                hasActiveSession = true,
                title = title,
                artist = artist,
                sourcePackage = controller.packageName,
                albumArt = art
            ),
            controller
        )
    }

    private fun cleanupListeners() {
        activeController?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        activeController = null
        runCatching {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
    }
}
