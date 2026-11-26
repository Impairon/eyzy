package com.abo.avatarblackout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranAudioModelTest {
    @Test
    fun surahCatalogueContainsCorrectFirstAndLastNames() {
        assertEquals("1. Al-Fatihah", QuranSurahs.name(1, "en"))
        assertEquals("114. An-Nas", QuranSurahs.name(114, "en"))
        assertEquals("1. الفاتحة", QuranSurahs.name(1, "ar"))
        assertEquals("114. الناس", QuranSurahs.name(114, "ar"))
    }

    @Test
    fun invalidSurahNumbersAreNotMappedToAnotherSurah() {
        assertEquals("0", QuranSurahs.name(0, "en"))
        assertEquals("115", QuranSurahs.name(115, "ar"))
    }

    @Test
    fun mainAndSideTimelinesCarryIndependentPositions() {
        val main = QuranTimelineState(
            id = QuranAudioRepository.MAIN_TIMELINE,
            chapters = (1..114).toList(),
            currentChapterIndex = 2,
            positionMs = 82_500L
        )
        val side = QuranTimelineState(
            id = QuranAudioRepository.SIDE_TIMELINE,
            chapters = listOf(18),
            currentChapterIndex = 0,
            positionMs = 19_250L
        )

        assertEquals(3, main.currentSurah)
        assertEquals(18, side.currentSurah)
        assertEquals(82_500L, main.positionMs)
        assertEquals(19_250L, side.positionMs)
    }

    @Test
    fun recitationMustContainAll114SurahsInOrder() {
        val complete = QuranRecitation(
            reciterId = "1",
            reciterName = "Test reciter",
            moshafId = "10",
            moshafName = "Complete",
            server = "https://audio.example/",
            surahs = (1..114).toList()
        )
        val partial = complete.copy(surahs = (1..113).toList())

        assertTrue(complete.supportsFullQuran())
        assertFalse(partial.supportsFullQuran())
        assertEquals("https://audio.example/018.mp3", complete.audioUrl(18))
    }
}
