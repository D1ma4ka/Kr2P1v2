package ru.krapiva.app.places

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun locationEnabled(context: Context): Boolean =
    context.getSystemService(LocationManager::class.java)?.let { LocationManagerCompat.isLocationEnabled(it) } == true

@Suppress("MissingPermission")
internal suspend fun currentLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
    val manager = context.getSystemService(LocationManager::class.java)
    if (manager == null || !hasLocationPermission(context) || !LocationManagerCompat.isLocationEnabled(manager)) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }
    val providers = manager.getProviders(true).filter {
        it == LocationManager.NETWORK_PROVIDER || it == LocationManager.GPS_PROVIDER || it == "fused"
    }
    val recent = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.filter { it.usable() }.minByOrNull { it.accuracy }
    if (recent != null) {
        continuation.resume(recent)
        return@suspendCancellableCoroutine
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!location.usable() || !continuation.isActive) return
            manager.removeUpdates(this)
            continuation.resume(location)
        }
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Android compatibility")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }
    continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
    val registered = providers.count { provider ->
        runCatching { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }.isSuccess
    }
    if (registered == 0 && continuation.isActive) continuation.resume(null)
}


internal val emulatedDevice: Boolean
    get() = android.os.Build.HARDWARE in setOf("goldfish", "ranchu") ||
        android.os.Build.MODEL.contains("sdk_gphone", ignoreCase = true)

@Suppress("DEPRECATION")
private fun Location.usable() = usableFix(latitude, longitude, SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos,
    hasAccuracy(), accuracy, isFromMockProvider, emulatedDevice)

internal fun usableFix(latitude: Double, longitude: Double, ageNanos: Long, hasAccuracy: Boolean,
                       accuracy: Float, mock: Boolean, emulator: Boolean): Boolean =
    latitude in -90.0..90.0 && longitude in -180.0..180.0 && ageNanos in 0..30_000_000_000L &&
        hasAccuracy && accuracy.isFinite() && accuracy >= 0f && (!mock || emulator)
