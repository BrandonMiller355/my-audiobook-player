package com.brandonmiller.audiobookplayer.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the one [ExoPlayer] in the app. The UI talks to it through a `MediaController`, never by
 * holding a player itself — that is what makes PRD §13 true by construction: audio survives the
 * screen turning off, the app being backgrounded, and the Activity being destroyed.
 *
 * Publishing a [MediaSession] is also what gives the notification, the lock screen, and Bluetooth
 * controls something to talk to (PRD §14), and it is the same player the UI drives, so they
 * cannot disagree about what is playing.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // SPEECH rather than MUSIC is deliberate: it tells the system this is spoken word, which
        // changes ducking decisions. An audiobook ducked like music becomes unintelligible
        // rather than merely quieter.
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(this)
            // handleAudioFocus = true gives PRD §15 behavior from the built-in implementation
            // rather than a hand-rolled one, which is a classic source of "resumed during a
            // phone call" bugs.
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            // Pause instead of suddenly playing out loud when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Swiping the app away should not leave a foreground service running with nothing playing.
     * If audio is still going, the service stays: that is the whole point of it.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
