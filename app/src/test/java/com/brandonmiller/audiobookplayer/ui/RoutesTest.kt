package com.brandonmiller.audiobookplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun `player route builder fills the declared argument placeholder`() {
        assertEquals("player/{bookId}", Routes.PLAYER)
        assertEquals("player/abc123", Routes.player("abc123"))
    }

    @Test
    fun `built player route matches the declared pattern shape`() {
        val pattern = Routes.PLAYER.replace("{${Routes.ARG_BOOK_ID}}", "([^/]+)").toRegex()
        val match = pattern.matchEntire(Routes.player("the-hero-of-ages"))
        assertEquals("the-hero-of-ages", match?.groupValues?.get(1))
    }
}
