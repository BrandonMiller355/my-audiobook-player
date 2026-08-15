package com.brandonmiller.audiobookplayer.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Wraps the session's player so that chapter navigation — from the in-app button, the notification,
 * or a Bluetooth remote — means the book's chapters rather than the playlist's items: "previous"
 * always applies the 3-second rule (PRD §7.4) instead of unconditionally jumping to the previous
 * item, and "next" moves to the next chapter's start.
 *
 * This has to live at the player level, not in the UI's click handler: a Bluetooth or car-head-unit
 * command reaches the session directly and never passes through
 * [com.brandonmiller.audiobookplayer.ui.player.PlayerViewModel]. Wrapping the player is what makes
 * every control surface agree (`add-playback-service` design D7 flagged this as the moment this
 * wrapper would become necessary).
 *
 * "Next" is wrapped for the same reason "previous" always was, one step later: Media3's default
 * "next item" already was "next chapter" for a folder book, but on a single-item `.m4b` book it has
 * nothing to do (design D1). For a folder book the wrapping is behavior-neutral — the next
 * chapter's start is position zero of the next item, which is where the default went.
 */
@OptIn(UnstableApi::class)
internal class ChapterAwarePlayer(player: Player) : ForwardingPlayer(player) {

    override fun seekToPrevious() = seekToPreviousChapter()

    override fun seekToPreviousMediaItem() = seekToPreviousChapter()

    override fun seekToNext() = seekToNextChapter()

    override fun seekToNextMediaItem() = seekToNextChapter()

    private fun seekToPreviousChapter() {
        val timeline = chapterTimeline()
        val target = timeline.previousChapterTarget(currentLocation(timeline))
        seekTo(target.mediaItemIndex, target.positionMs)
    }

    private fun seekToNextChapter() {
        val timeline = chapterTimeline()
        // Null at the final chapter — PRD §7.4's "do nothing" case.
        val target = timeline.nextChapterTarget(currentLocation(timeline)) ?: return
        seekTo(target.mediaItemIndex, target.positionMs)
    }

    /**
     * The wrapped rules answer for themselves whether there is anywhere to go, so both directions
     * must always report as available. ExoPlayer's own logic would otherwise disable "previous" at
     * the very start of the playlist and "next" on a single-item book, since its default behavior
     * really does have nothing to do in either case — and a disabled command is a control surface
     * that never calls this at all.
     */
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .build()
}
