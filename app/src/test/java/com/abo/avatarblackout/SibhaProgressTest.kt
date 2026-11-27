package com.abo.avatarblackout

import org.junit.Assert.assertEquals
import org.junit.Test

class SibhaProgressTest {
    @Test
    fun startsAtZero() {
        assertEquals(0, SibhaProgress.progress(0))
    }

    @Test
    fun tracksProgressWithinCycle() {
        assertEquals(1, SibhaProgress.progress(1))
        assertEquals(17, SibhaProgress.progress(17))
        assertEquals(33, SibhaProgress.progress(33))
    }

    @Test
    fun startsNewCycleAfterThirtyThree() {
        assertEquals(1, SibhaProgress.progress(34))
        assertEquals(2, SibhaProgress.progress(35))
        assertEquals(33, SibhaProgress.progress(66))
        assertEquals(1, SibhaProgress.progress(67))
    }

    @Test
    fun negativeCountsAreTreatedAsZero() {
        assertEquals(0, SibhaProgress.progress(-1))
    }
}
