package com.brandonmiller.audiobookplayer.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Wraps the session's player so that "previous" — from the in-app button, the notification, or a
 * Bluetooth remote — always applies the 3-second rule (PRD §7.4), rather than Media3's default of
 * unconditionally jumping to the previous item.
 *
 * This has to live at the player level, not in the UI's click handler: a Bluetooth or car-head-unit
 * "previous" command reaches the session directly and never passes through
 * [com.brandonmiller.audiobookplayer.ui.player.PlayerViewModel]. Wrapping the player is what makes
 * every control surface agree (`add-playback-service` design D7 flagged this as the moment this
 * wrapper would become necessary).
 */
@OptIn(UnstableApi::class)
internal class ChapterAwarePlayer(player: Player) : ForwardingPlayer(player) {

    override fun seekToPrevious() = seekToPreviousChapter()

    override fun seekToPreviousMediaItem() = seekToPreviousChapter()

    private fun seekToPreviousChapter() {
        val timeline = chapterTimeline()
        val target = timeline.previousChapterTarget(currentLocation(timeline))
        seekTo(target.mediaItemIndex, target.positionMs)
    }

    /**
     * The wrapped rule always has a target — restarting the first chapter rather than no-op — so
     * "previous" must always report as available. ExoPlayer's own logic would otherwise disable it
     * at the very start of the playlist, since its default behavior really does have nothing to do
     * there.
     */
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
}
