package app.katib.audio

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One-shot location from the platform LocationManager (no Google Play services, no network geocoding). */
object Locator {
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val provider = when {
            Build.VERSION.SDK_INT >= 31 && lm.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> LocationManager.NETWORK_PROVIDER
        }
        val fresh = if (Build.VERSION.SDK_INT >= 30) runCatching {
            withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine { c ->
                    lm.getCurrentLocation(provider, null, ctx.mainExecutor) { c.resume(it) }
                }
            }
        }.getOrNull() else null
        return fresh ?: runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull()
    }
}
