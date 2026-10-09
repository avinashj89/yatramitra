package com.avinash.yatramitra.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.HospitalFinder
import com.avinash.yatramitra.data.HospitalRules
import com.avinash.yatramitra.data.RoutePlans
import com.avinash.yatramitra.data.RouteRepository
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.model.DayHospitals
import com.avinash.yatramitra.model.Hospital
import com.avinash.yatramitra.model.RouteDay
import com.avinash.yatramitra.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Every saved day's hospital list for a trip, live. Null until the first read arrives. */
@Composable
fun rememberTripHospitals(tripCode: String): Map<Int, DayHospitals>? {
    var saved by remember(tripCode) { mutableStateOf<Map<Int, DayHospitals>?>(null) }
    LaunchedEffect(tripCode) { TripRepository.observeHospitals(tripCode).collect { saved = it } }
    return saved
}

/** Phone and Maps hand-offs used by the hospital list and SOS. */
object EmergencyActions {
    /** Opens the dialer with the number filled in; the person still taps Call. */
    fun dial(context: Context, number: String) = launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' })))

    fun directions(context: Context, lat: Double, lng: Double) =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving")))

    /** Google Maps search, e.g. a hospital by name (its listing shows the phone number), or
     *  "petrol pump" near the phone. */
    fun searchMaps(context: Context, query: String) =
        launch(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(query, "UTF-8"))))

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can open that.", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * Route tab card: prominent hospitals within 4 km of the selected day's route. Looked up once
 * and saved for the whole group (so it also works without signal), and looked up again when that
 * day's route changes.
 */
@Composable
fun HospitalsCard(
    tripCode: String,
    days: List<RouteDay>,
    dayIndex: Int,
    routeInfo: RouteRepository.RouteInfo?,
    canUpdate: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = rememberTripHospitals(tripCode)
    val day = days.getOrNull(dayIndex) ?: return
    val key = HospitalRules.routeKey(day)
    val savedDay = saved?.get(dayIndex)
    val upToDate = savedDay != null && savedDay.routeKey == key
    val hasRoute = RoutePlans.hasRoute(day)
    var finding by remember(dayIndex, key) { mutableStateOf(false) }
    var failure by remember(dayIndex, key) { mutableStateOf<String?>(null) }
    var showAllDays by remember { mutableStateOf(false) }

    fun find() {
        finding = true
        failure = null
        scope.launch {
            try {
                val route = routeInfo ?: RouteRepository.fetchRouteSummary(RoutePlans.placesInOrder(day))
                    ?: throw java.io.IOException("couldn't work out the route")
                val hospitals = HospitalFinder.findAlong(route)
                TripRepository.saveHospitals(tripCode, DayHospitals(dayIndex, key, System.currentTimeMillis(), hospitals))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = "Couldn't look up hospitals (${e.message ?: "network problem"}). Check your internet and try again."
            } finally {
                finding = false
            }
        }
    }

    // Look up automatically once the route is known and there's no list for it yet.
    LaunchedEffect(dayIndex, key, saved != null, upToDate, routeInfo != null) {
        if (canUpdate && saved != null && hasRoute && !upToDate && routeInfo != null && !finding && failure == null) find()
    }

    val dayLabel = if (days.size > 1) "Day ${dayIndex + 1}" else "the route"
    HospitalsCardBody(
        dayLabel = dayLabel,
        updatedAtMillis = savedDay?.generatedAtMillis?.takeIf { upToDate },
        hasRoute = hasRoute,
        finding = finding,
        failure = failure,
        hospitals = savedDay?.hospitals,
        routeChanged = savedDay != null && !upToDate,
        canUpdate = canUpdate,
        multiDay = days.size > 1,
        onFind = ::find,
        onShowAllDays = { showAllDays = true },
        onSearchMaps = { EmergencyActions.searchMaps(context, "hospitals near ${day.from.name}") }
    )

    if (showAllDays) {
        AllDaysHospitalsDialog(days = days, saved = saved.orEmpty(), onDismiss = { showAllDays = false })
    }
}

/** The hospitals card's drawing, separate from loading and saving the data. */
@Composable
fun HospitalsCardBody(
    dayLabel: String,
    updatedAtMillis: Long?,
    hasRoute: Boolean,
    finding: Boolean,
    failure: String?,
    hospitals: List<Hospital>?,
    routeChanged: Boolean,
    canUpdate: Boolean,
    multiDay: Boolean,
    onFind: () -> Unit,
    onShowAllDays: () -> Unit,
    onSearchMaps: () -> Unit
) {
    ElevatedCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.LocalHospital, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hospitals along $dayLabel", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (updatedAtMillis != null) "Within 4 km of the route · updated ${formatUpdated(updatedAtMillis)}" else "Within 4 km of the route",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canUpdate && hasRoute && !finding) {
                    IconButton(onClick = onFind) { Icon(Icons.Filled.Refresh, contentDescription = "Look up hospitals again") }
                }
            }

            when {
                !hasRoute -> Text(
                    "Add this day's From and To to see hospitals along the way.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                finding -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Finding hospitals along the route…", style = MaterialTheme.typography.bodyMedium)
                }
                failure != null -> {
                    Text(failure!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onFind) { Text("Try again") }
                }
                hospitals == null -> Text(
                    if (canUpdate) "Hospitals will appear once the route is worked out." else "Not looked up yet for this day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    val list = hospitals
                    if (routeChanged) {
                        Text(
                            "This day's route changed since this list was made.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (list.isEmpty()) {
                        Text(
                            "No hospitals are listed within 4 km of this route on OpenStreetMap.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onSearchMaps) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Search hospitals on Google Maps")
                        }
                    } else {
                        list.forEach { HospitalRow(it, "About ${it.kmFromStart.roundToInt()} km into $dayLabel") }
                    }
                }
            }

            EmergencyNumbers()
            if (multiDay) {
                TextButton(onClick = onShowAllDays) { Text("See hospitals for all days") }
            }
            Text(
                "Hospital data © OpenStreetMap contributors. It can be out of date: call ahead when you can.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

}

@Composable
fun HospitalRow(hospital: Hospital, distanceLine: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hospital.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (hospital.emergency) {
                Text(
                    "Emergency",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            distanceLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hospital.address.isNotBlank()) Text(hospital.address, style = MaterialTheme.typography.bodySmall)
        if (hospital.phone.isNotBlank()) Text("Phone: ${hospital.phone}", style = MaterialTheme.typography.bodySmall)
        val compact = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (hospital.phone.isNotBlank()) {
                FilledTonalButton(
                    onClick = { EmergencyActions.dial(context, hospital.phone) },
                    contentPadding = compact,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Call", maxLines = 1)
                }
            } else {
                OutlinedButton(
                    onClick = { EmergencyActions.searchMaps(context, "${hospital.name} ${hospital.address}".trim()) },
                    contentPadding = compact,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Find phone", maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = { EmergencyActions.directions(context, hospital.lat, hospital.lng) },
                contentPadding = compact,
                modifier = Modifier.weight(1.7f)
            ) {
                Text("Open in Google Maps", maxLines = 1)
            }
        }
    }
}

/** India's national emergency numbers, one tap to the dialer. */
@Composable
fun EmergencyNumbers() {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = { EmergencyActions.dial(context, "112") }, label = { Text("112 Emergency") }, leadingIcon = { Icon(Icons.Filled.Call, null, Modifier.size(16.dp)) })
        AssistChip(onClick = { EmergencyActions.dial(context, "108") }, label = { Text("108 Ambulance") }, leadingIcon = { Icon(Icons.Filled.Call, null, Modifier.size(16.dp)) })
    }
}

/** Every day's saved list at once. */
@Composable
fun AllDaysHospitalsDialog(days: List<RouteDay>, saved: Map<Int, DayHospitals>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hospitals along every day") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                days.forEachIndexed { index, day ->
                    val label = "Day ${index + 1}"
                    Text(
                        "$label: ${RoutePlans.placesInOrder(day).joinToString(" → ") { it.name }.ifBlank { "route not set" }}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    val list = saved[index]?.hospitals
                    when {
                        list == null -> Text(
                            "Not looked up yet. Open $label on the Route tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        list.isEmpty() -> Text("None listed within 4 km.", style = MaterialTheme.typography.bodySmall)
                        else -> list.forEach { HospitalRow(it, "About ${it.kmFromStart.roundToInt()} km into $label") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun formatUpdated(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
