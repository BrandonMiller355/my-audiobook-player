package com.brandonmiller.audiobookplayer.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.brandonmiller.audiobookplayer.data.AudiobookDatabase
import com.brandonmiller.audiobookplayer.data.LibraryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the one [ExoPlayer] in the app. The UI talks to it through a `MediaController`, never by
 * holding a player itself — that is what makes PRD §13 true by construction: audio survives the
 * screen turning off, the app being backgrounded, and the Activity being destroyed.
 *
 * Publishing a [MediaSession] is also what gives the notification, the lock screen, and Bluetooth
 * controls something to talk to (PRD §14), and it is the same player the UI drives, so they
 * cannot disagree about what is playing.
 *
 * It also persists playback progress itself, not the UI (`add-transport-controls` design D6):
 * the service is the component that actually outlives the Activity, so it is the only place PRD
 * §12's "on service shutdown" example can be honored. The book id is read out of the current
 * [MediaItem]'s media id — the service does not otherwise track which book is loaded.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var dao: LibraryDao

    // Main.immediate: MediaSession enforces that its player is only touched from the app's main
    // thread ("Player callback method is called from a wrong thread"), so every read of
    // mediaSession.player has to happen here. Only the actual Room write below hops to IO.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressTicker: Job? = null

    // Progress writes happen from several call sites (pause, item transition, the ticker,
    // shutdown); a lock keeps them from interleaving into an inconsistent write.
    private val progressLock = Mutex()

    private val progressListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startProgressTicker()
            } else {
                progressTicker?.cancel()
                writeProgress()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = writeProgress()
    }

    override fun onCreate() {
        super.onCreate()
        dao = AudiobookDatabase.get(this).libraryDao()

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
        player.addListener(progressListener)

        // Wrapped so "previous" — from any control surface — applies the 3-second rule rather
        // than Media3's unconditional jump to the previous item.
        mediaSession = MediaSession.Builder(this, ChapterAwarePlayer(player)).build()
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
        progressTicker?.cancel()
        // Blocking, deliberately: the player is about to be released and this is the app's last
        // chance to record where playback actually stopped (PRD §12's "on service shutdown").
        runBlocking { writeProgressSuspending() }
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startProgressTicker() {
        progressTicker?.cancel()
        progressTicker = serviceScope.launch {
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                writeProgressSuspending()
            }
        }
    }

    private fun writeProgress() {
        serviceScope.launch { writeProgressSuspending() }
    }

    private suspend fun writeProgressSuspending() {
        // Player reads must stay on the main thread (see serviceScope's dispatcher note above).
        val player = mediaSession?.player ?: return
        val bookId = mediaIdBookId(player.currentMediaItem?.mediaId) ?: return
        val mediaItemIndex = player.currentMediaItemIndex
        val positionMs = player.currentPosition.coerceAtLeast(0)

        withContext(Dispatchers.IO) {
            progressLock.withLock {
                dao.updateProgress(bookId, mediaItemIndex, positionMs, System.currentTimeMillis())
            }
        }
    }

    private companion object {
        const val PROGRESS_TICK_MS = 15_000L
    }
}
