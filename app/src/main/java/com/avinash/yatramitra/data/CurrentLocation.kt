package com.avinash.yatramitra.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.PlaceSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** The phone's current position as a route place ("Use my current location"), and for SOS. */
object CurrentLocation {

    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Why the location couldn't be read, worded for the person holding the phone. */
    class Unavailable(message: String) : Exception(message)

    fun hasPermission(context: Context): Boolean =
        PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private fun hasPrecise(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fresh fix (waiting up to [timeoutMillis]), else the phone's last known one, with a
     *  readable name. */
    @SuppressLint("MissingPermission") // checked by hasPermission() first
    suspend fun asPlace(context: Context, timeoutMillis: Long = 15_000): Place {
        if (!hasPermission(context)) throw Unavailable("Allow location access to use your current location.")
        val client = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (hasPrecise(context)) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val tokenSource = CancellationTokenSource()
        val location = try {
            withTimeoutOrNull(timeoutMillis) { client.getCurrentLocation(priority, tokenSource.token).await() }
                ?: client.lastLocation.await()
        } catch (e: SecurityException) {
            null
        } finally {
            tokenSource.cancel()
        } ?: throw Unavailable("Couldn't get your location. Turn on Location (GPS) and try again.")

        val (name, address) = describe(context, location.latitude, location.longitude)
        return Place(
            name = name,
            address = address,
            lat = location.latitude,
            lng = location.longitude,
            source = PlaceSource.CURRENT_LOCATION,
            locatedAtMillis = System.currentTimeMillis()
        )
    }

    /** A short name and a full address for a position, using the phone's own (free) lookup. */
    @Suppress("DEPRECATION") // the listener version needs Android 13; this one works everywhere
    private suspend fun describe(context: Context, lat: Double, lng: Double): Pair<String, String> = withContext(Dispatchers.IO) {
        val found = runCatching {
            if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull() else null
        }.getOrNull()
        val name = PlaceRules.currentLocationName(found?.thoroughfare, found?.subLocality, found?.locality, lat, lng)
        name to (found?.getAddressLine(0) ?: "")
    }
}
