package com.abo.avatarblackout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AudioBookModelTest {
    @Test
    fun booksKeepIndependentTrackAndPositionState() {
        val first = AudioBook(
            id = "main-audio",
            name = "Main course",
            tracks = listOf(
                AudioBookTrack("/audio/one.mp3", "Opening", artist = "Reciter", durationMs = 120_000L),
                AudioBookTrack("/audio/two.mp3", "Second part", artist = "Reciter", durationMs = 180_000L)
            ),
            currentTrackIndex = 1,
            positionMs = 75_000L
        )
        val second = AudioBook(
            id = "side-audio",
            name = "Side listening",
            tracks = listOf(AudioBookTrack("/audio/random.mp3", "Random chapter")),
            positionMs = 12_500L
        )

        assertEquals("Second part", first.currentTrack?.title)
        assertEquals(75_000L, first.positionMs)
        assertEquals("Random chapter", second.currentTrack?.title)
        assertEquals(12_500L, second.positionMs)
        assertNotEquals(first.positionMs, second.positionMs)
    }

    @Test
    fun bookmarksPointToTrackAndPositionWithoutRenamingTrackMetadata() {
        val track = AudioBookTrack(
            path = "/audio/chapter.mp3",
            title = "Original embedded title",
            artist = "Original artist"
        )
        val bookmark = AudioBookBookmark(
            id = "bookmark-a",
            name = "Important passage",
            trackIndex = 0,
            positionMs = 42_250L
        )
        val book = AudioBook(
            id = "book-a",
            name = "My imported book",
            tracks = listOf(track),
            bookmarks = listOf(bookmark)
        )

        assertEquals("Important passage", book.bookmarks.single().name)
        assertEquals(0, book.bookmarks.single().trackIndex)
        assertEquals(42_250L, book.bookmarks.single().positionMs)
        assertEquals("Original embedded title", book.tracks.single().title)
        assertEquals("Original artist", book.tracks.single().artist)
    }
}
