package ru.krapiva.app.designsystem

import org.junit.Assert.*
import org.junit.Test

class ClubPhrasesTest {
    @Test fun completePhraseSpaceIsUniqueAndReadable() {
        assertTrue(ClubPhrases.count >= 5000)
        val phrases = (0 until ClubPhrases.count).map(ClubPhrases::at)
        assertEquals(ClubPhrases.count, phrases.toSet().size)
        assertTrue(phrases.all { it.length in 30..240 && !it.contains("null") })
        assertEquals(ClubPhrases.at(0), ClubPhrases.at(ClubPhrases.count))
        assertEquals(ClubPhrases.at(ClubPhrases.count - 1), ClubPhrases.at(-1))
        assertEquals(ClubPhrases.forCheckin("checkin-1"), ClubPhrases.forCheckin("checkin-1"))
    }
    @Test fun motionSettlesAndNeverProducesInvalidGeometry() {
        assertEquals(0f, ClubMotion.progress(-100), 0f)
        assertEquals(.5f, ClubMotion.progress(900), .001f)
        assertEquals(1f, ClubMotion.progress(Long.MAX_VALUE), 0f)
        assertEquals(1f, ClubMotion.progress(0, reduced = true), 0f)
        for(time in 0L..3000L step 25) assertTrue(ClubMotion.progress(time) in 0f..1f)
    }
}
