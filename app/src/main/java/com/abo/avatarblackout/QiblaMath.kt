package com.abo.avatarblackout

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure geographic and angular calculations used by the Qibla compass.
 *
 * Bearings are clockwise from north. Android's compass heading is magnetic,
 * so magneticBearingFromTrue() converts the geographic target into the same
 * magnetic-north reference frame before the two values are compared.
 */
internal object QiblaMath {
    const val KAABA_LATITUDE = 21.4225
    const val KAABA_LONGITUDE = 39.8262

    fun normalize360(degrees: Double): Double =
        ((degrees % 360.0) + 360.0) % 360.0

    fun normalize360(degrees: Float): Float =
        ((degrees % 360f) + 360f) % 360f

    /** Returns a signed shortest angle in (-180, 180]. */
    fun normalizeSigned(degrees: Float): Float {
        val normalized = normalize360(degrees)
        return if (normalized > 180f) normalized - 360f else normalized
    }

    /**
     * Initial great-circle bearing from the user's WGS84 latitude/longitude
     * to the Kaaba, measured clockwise from true north.
     */
    fun initialBearing(latitude: Double, longitude: Double): Double {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be finite and between -90 and 90 degrees."
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be finite and between -180 and 180 degrees."
        }

        val userLat = Math.toRadians(latitude)
        val userLon = Math.toRadians(longitude)
        val kaabaLat = Math.toRadians(KAABA_LATITUDE)
        val kaabaLon = Math.toRadians(KAABA_LONGITUDE)
        val deltaLongitude = kaabaLon - userLon

        val y = sin(deltaLongitude) * cos(kaabaLat)
        val x = cos(userLat) * sin(kaabaLat) -
            sin(userLat) * cos(kaabaLat) * cos(deltaLongitude)

        require(abs(x) + abs(y) > 1e-12) {
            "Qibla bearing is undefined at the Kaaba coordinates."
        }

        return normalize360(Math.toDegrees(atan2(y, x)))
    }

    /**
     * Converts a true-north target bearing to a magnetic-compass bearing.
     * Android GeomagneticField declination is positive when magnetic north is
     * east of true north, so the target's magnetic bearing is true - declination.
     */
    fun magneticBearingFromTrue(trueBearingDegrees: Double, declinationDegrees: Double): Double {
        require(trueBearingDegrees.isFinite() && declinationDegrees.isFinite())
        return normalize360(trueBearingDegrees - declinationDegrees)
    }

    /** Positive means the Qibla is clockwise/right of the phone's top edge. */
    fun relativeQiblaAngle(qiblaBearing: Float, phoneHeading: Float): Float =
        normalizeSigned(qiblaBearing - phoneHeading)

    /** Circular interpolation that takes the shortest path across 0/360 degrees. */
    fun smoothAngle(current: Float, target: Float, factor: Float): Float {
        val safeFactor = factor.coerceIn(0f, 1f)
        return normalize360(current + normalizeSigned(target - current) * safeFactor)
    }
}
