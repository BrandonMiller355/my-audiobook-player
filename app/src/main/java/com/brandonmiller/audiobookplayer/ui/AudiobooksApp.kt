package com.brandonmiller.audiobookplayer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brandonmiller.audiobookplayer.ui.library.LibraryScreen
import com.brandonmiller.audiobookplayer.ui.notes.NotesScreen
import com.brandonmiller.audiobookplayer.ui.player.PlayerScreen
import com.brandonmiller.audiobookplayer.ui.reader.ReaderScreen

object Routes {
    const val LIBRARY = "library"

    const val ARG_BOOK_ID = "bookId"
    const val PLAYER = "player/{$ARG_BOOK_ID}"

    /**
     * The Reader is its own destination rather than a second face of the Player
     * (`add-ebook-companion` design D5). As a toggle inside the Player, hardware back from the
     * reading page would drop the user at the Library, skipping the Player entirely.
     */
    const val READER = "reader/{$ARG_BOOK_ID}"

    /**
     * The notes for one book. A destination rather than a sheet for two reasons
     * (`add-notes-and-bookmarks` design D7): editing note text under a partially expanded sheet puts
     * a soft keyboard where the content is, and `ChaptersSheet` is already carrying two sections.
     *
     * [ARG_NOTE_ID] is optional and set only when the Player's snackbar sends the user straight to
     * the note it just took, so that marking and writing up read as one flow. Absent means "just
     * show the list".
     */
    const val ARG_NOTE_ID = "noteId"
    const val NOTES = "notes/{$ARG_BOOK_ID}?$ARG_NOTE_ID={$ARG_NOTE_ID}"

    fun player(bookId: String) = "player/$bookId"

    fun reader(bookId: String) = "reader/$bookId"

    fun notes(bookId: String, noteId: Long? = null) =
        if (noteId == null) "notes/$bookId" else "notes/$bookId?$ARG_NOTE_ID=$noteId"
}

@Composable
fun AudiobooksApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBookClick = { bookId -> navController.navigate(Routes.player(bookId)) },
                onEbookClick = { bookId -> navController.navigate(Routes.reader(bookId)) },
            )
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
        ) { entry ->
            val bookId = entry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty()
            PlayerScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onOpenReader = { navController.navigate(Routes.reader(bookId)) },
                onOpenNotes = { noteId -> navController.navigate(Routes.notes(bookId, noteId)) },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
        ) { entry ->
            val bookId = entry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty()
            ReaderScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onOpenNotes = { noteId -> navController.navigate(Routes.notes(bookId, noteId)) },
                // A book that was an ebook alone has just gained its audio. The Player takes the
                // Reader's place rather than stacking on it, so the back stack is the one any book
                // with audio has — Library, Player — and the Reader is one tap away on the Player's
                // ebook control, at the same reading position (`add-standalone-ebooks` design D6).
                onAudioAdded = {
                    navController.navigate(Routes.player(bookId)) {
                        popUpTo(Routes.READER) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.NOTES,
            arguments = listOf(
                navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType },
                // -1 rather than null: the argument is a long, and "no note named" needs a value
                // the route can carry rather than an absent one the parser has to invent.
                navArgument(Routes.ARG_NOTE_ID) { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            NotesScreen(
                bookId = entry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty(),
                openNoteId = entry.arguments?.getLong(Routes.ARG_NOTE_ID)?.takeIf { it > 0 },
                // Back to the Player, never past it to the Library — the same reason the Reader is
                // its own destination rather than a face of the Player.
                onBack = { navController.popBackStack() },
            )
        }
    }
}
