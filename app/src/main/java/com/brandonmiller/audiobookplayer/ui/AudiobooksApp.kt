package com.brandonmiller.audiobookplayer.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.brandonmiller.audiobookplayer.ui.library.LibraryScreen
import com.brandonmiller.audiobookplayer.ui.player.PlayerScreen

object Routes {
    const val LIBRARY = "library"

    const val ARG_BOOK_ID = "bookId"
    const val PLAYER = "player/{$ARG_BOOK_ID}"

    fun player(bookId: String) = "player/$bookId"
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
            PlayerScreen(
                bookId = entry.arguments?.getString(Routes.ARG_BOOK_ID).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
