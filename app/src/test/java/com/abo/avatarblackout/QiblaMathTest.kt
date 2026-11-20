package com.abo.avatarblackout

import org.junit.Assert.assertEquals
import org.junit.Test

class QiblaMathTest {
    @Test
    fun calculatesKnownInitialBearings() {
        assertEquals(136.1373, QiblaMath.initialBearing(30.0444, 31.2357), 0.01)
        assertEquals(58.4817, QiblaMath.initialBearing(40.7128, -74.0060), 0.01)
        assertEquals(118.9872, QiblaMath.initialBearing(51.5074, -0.1278), 0.01)
    }

    @Test
    fun convertsTrueBearingToMagneticReference() {
        assertEquals(95.0, QiblaMath.magneticBearingFromTrue(100.0, 5.0), 0.0001)
        assertEquals(105.0, QiblaMath.magneticBearingFromTrue(100.0, -5.0), 0.0001)
        assertEquals(3.0, QiblaMath.magneticBearingFromTrue(358.0, -5.0), 0.0001)
    }

    @Test
    fun relativeAngleUsesRightPositiveAndLeftNegative() {
        assertEquals(24f, QiblaMath.relativeQiblaAngle(136f, 112f), 0.001f)
        assertEquals(-12f, QiblaMath.relativeQiblaAngle(136f, 148f), 0.001f)
        assertEquals(20f, QiblaMath.relativeQiblaAngle(10f, 350f), 0.001f)
        assertEquals(-20f, QiblaMath.relativeQiblaAngle(350f, 10f), 0.001f)
    }

    @Test
    fun normalizationAndSmoothingHandleNorthWraparound() {
        assertEquals(350f, QiblaMath.normalize360(-10f), 0.001f)
        assertEquals(-10f, QiblaMath.normalizeSigned(350f), 0.001f)
        assertEquals(0f, QiblaMath.smoothAngle(359f, 1f, 0.5f), 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidLatitude() {
        QiblaMath.initialBearing(95.0, 31.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidLongitude() {
        QiblaMath.initialBearing(30.0, 200.0)
    }
}
