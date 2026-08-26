package com.brandonmiller.audiobookplayer.playback

/**
 * How far behind the tap a mark is anchored (`add-notes-and-bookmarks` design D5).
 *
 * Anchoring at the moment of the tap is wrong, because the tap is not the moment of interest. The
 * passage plays, the listener registers it a few seconds later, gets the phone out, and taps —
 * perhaps twelve seconds after the thing they wanted to keep. Anchored at "now", every note would
 * need scrubbing backward on review, which is the friction this feature exists to remove.
 *
 * A constant rather than a preference: PRD §20 requires no settings screen, and this is a figure the
 * owner would set once and never revisit. Tunable here, in source, if device use argues for a
 * different number.
 */
const val NOTE_LEAD_IN_MS = 15_000L

/** Where a note points, plus the chapter title to store alongside it. */
data class NoteAnchor(val target: PlayerTarget, val chapterTitle: String)

/**
 * Works out where a mark taken at [from] should point, and which chapter title to snapshot onto it.
 *
 * The whole computation is two [BookTimeline] calls, which is why this feature needed no new
 * timeline arithmetic. [BookTimeline.seekTarget] already clamps at the start of the book, rolls back
 * into the previous chapter when that chapter's extent is known, and clamps at the current chapter's
 * start when it is not — so every edge case here is one that was already solved and tested for the
 * seek buttons.
 *
 * **The second call is the one that matters.** The title comes from locating the *anchored* target,
 * never [from]. A mark taken five seconds into a chapter correctly lands in the tail of the previous
 * one, and the label has to say so — otherwise the entry names a chapter that is not where it seeks,
 * which is the failure design D4 and D5 both point at and the one that would survive a test of the
 * anchor arithmetic alone.
 *
 * [chapterTitles] is indexed by chapter, as stored. A title that is not there yields an empty
 * string rather than throwing: a note is worth keeping even from a book whose chapter rows have not
 * loaded, and the anchor — the part that cannot be recovered later — is already correct by then.
 */
fun noteAnchorFor(
    timeline: BookTimeline,
    from: Location,
    chapterTitles: List<String>,
): NoteAnchor {
    val target = timeline.seekTarget(from, -NOTE_LEAD_IN_MS)
    val anchored = timeline.locate(target.mediaItemIndex, target.positionMs)
    return NoteAnchor(target, chapterTitles.getOrNull(anchored.chapterIndex).orEmpty())
}
