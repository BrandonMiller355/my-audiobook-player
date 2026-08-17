package com.brandonmiller.audiobookplayer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brandonmiller.audiobookplayer.ui.library.LibraryScreen
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

    fun player(bookId: String) = "player/$bookId"

    fun reader(bookId: String) = "reader/$bookId"
}

@Composable
fun AudiobooksApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBookClick = { bookId -> navController.navigate(Routes.player(bookId)) },
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
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.StringType }),
        ) { entry ->
            ReaderScreen(
                bookId = entry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
