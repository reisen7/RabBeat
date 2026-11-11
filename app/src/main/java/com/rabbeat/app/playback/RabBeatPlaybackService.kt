package com.rabbeat.app.playback

import android.content.Intent
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rabbeat.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RabBeatPlaybackService : MediaSessionService() {
    @Inject lateinit var playerController: PlayerController

    override fun onCreate() {
        super.onCreate()
        playerController.warmUp()
        val provider = DefaultMediaNotificationProvider(this)
        provider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(provider)
        playerController.mediaSession?.let { session ->
            if (!isSessionAdded(session)) addSession(session)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        playerController.mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val playing = playerController.state.value.isPlaying
        if (!playing) {
            stopSelf()
        }
    }
}
