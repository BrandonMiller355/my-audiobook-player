package com.brandonmiller.audiobookplayer.m4b

/**
 * One chapter mark within a single-file audiobook.
 *
 * Times are milliseconds from the start of the file. [endMs] is derived — neither chapter
 * encoding stores it — so it is the next chapter's start, or the file duration for the last.
 */
data class Chapter(
    val index: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * The outcome of reading chapter marks out of an `.m4b`.
 *
 * [Unchaptered] and [Unreadable] are deliberately distinct: "this book has no chapter marks"
 * is a normal state that should hide chapter UI, while "something went wrong reading it" is a
 * condition worth surfacing. Collapsing both into an empty list would lose that difference.
 */
sealed interface ChapterParseResult {

    /** Two or more usable marks, ordered by start time, with ends chained. */
    data class Chapters(val chapters: List<Chapter>) : ChapterParseResult

    /**
     * No usable chapter marks. Either the file carries none at all, or it carries a single
     * mark spanning the whole file, which conveys nothing to navigate by.
     */
    data object Unchaptered : ChapterParseResult

    /** The bytes could not be read as an MP4, or the chapter data within them was unusable. */
    data class Unreadable(val reason: String) : ChapterParseResult
}
