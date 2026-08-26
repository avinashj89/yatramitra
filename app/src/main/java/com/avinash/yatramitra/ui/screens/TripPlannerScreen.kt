package com.avinash.yatramitra.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.PlacesRepository
import com.avinash.yatramitra.data.RouteRepository
import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.ItineraryDay
import com.avinash.yatramitra.model.ItineraryStop
import com.avinash.yatramitra.model.PlaceSuggestion
import com.avinash.yatramitra.model.RoutePlan
import com.avinash.yatramitra.model.RoutePreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPlannerScreen() {
    val context = LocalContext.current
    var plan by remember { mutableStateOf(RoutePlan()) }
    var loaded by remember { mutableStateOf(false) }
    var findingPitstops by remember { mutableStateOf(false) }
    var pitstopMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        plan = LocalStore.loadRoutePlan(context)
        loaded = true
    }

    fun updatePlan(new: RoutePlan) {
        plan = new
        LocalStore.saveRoutePlan(context, new)
    }

    if (!loaded) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Plan your route", style = MaterialTheme.typography.titleLarge)
        Text(
            "Enter where you're starting and where you're headed. YatraMitra opens Google Maps for turn-by-turn navigation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        OutlinedTextField(
            value = plan.tripName,
            onValueChange = { updatePlan(plan.copy(tripName = it)) },
            label = { Text("Trip name") },
            placeholder = { Text("e.g. Coorg long weekend") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        AutocompletePlaceField(
            label = "From",
            value = plan.from,
            onValueChange = { updatePlan(plan.copy(from = it)) }
        )

        plan.toStops.forEachIndexed { index, stopValue ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AutocompletePlaceField(
                    label = if (plan.toStops.size == 1) "To" else "To ${index + 1}",
                    value = stopValue,
                    onValueChange = { new ->
                        val updated = plan.toStops.toMutableList().also { it[index] = new }
                        updatePlan(plan.copy(toStops = updated))
                    },
                    modifier = Modifier.weight(1f)
                )
                if (plan.toStops.size > 1) {
                    IconButton(onClick = {
                        val updated = plan.toStops.toMutableList().also { it.removeAt(index) }
                        updatePlan(plan.copy(toStops = updated))
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove this stop")
                    }
                }
                if (index == plan.toStops.lastIndex && plan.toStops.size < RoutePlan.MAX_TO_STOPS) {
                    IconButton(onClick = {
                        updatePlan(plan.copy(toStops = plan.toStops + ""))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add another stop")
                    }
                }
            }
        }
        if (plan.toStops.size >= RoutePlan.MAX_TO_STOPS) {
            Text(
                "Up to ${RoutePlan.MAX_TO_STOPS} stops — that's the max for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = plan.roundTrip,
                onCheckedChange = { updatePlan(plan.copy(roundTrip = it)) }
            )
            Spacer(Modifier.width(4.dp))
            Text("This is a round trip (I'll return to the start)")
        }

        HorizontalDivider()

        Text("Break at every", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = plan.breakEvery,
                onValueChange = { new -> if (new.all { it.isDigit() }) updatePlan(plan.copy(breakEvery = new)) },
                label = { Text("Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                BreakUnitOption(
                    label = "Kilometers",
                    selected = plan.breakUnit == BreakUnit.KM,
                    onSelect = { updatePlan(plan.copy(breakUnit = BreakUnit.KM)) }
                )
                BreakUnitOption(
                    label = "Hours",
                    selected = plan.breakUnit == BreakUnit.HOURS,
                    onSelect = { updatePlan(plan.copy(breakUnit = BreakUnit.HOURS)) }
                )
            }
        }

        HorizontalDivider()

        Text("Route preference", style = MaterialTheme.typography.titleMedium)
        RoutePrefOption(
            label = "Fastest route",
            selected = plan.routePreference == RoutePreference.FASTEST,
            onSelect = { updatePlan(plan.copy(routePreference = RoutePreference.FASTEST)) }
        )
        RoutePrefOption(
            label = "Surprise me",
            selected = plan.routePreference == RoutePreference.SURPRISE,
            onSelect = { updatePlan(plan.copy(routePreference = RoutePreference.SURPRISE)) }
        )
        if (plan.routePreference == RoutePreference.SURPRISE) {
            Text(
                "Google Maps will open with directions — tap \"Alternate routes\" inside Maps for something other than the fastest one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(8.dp))

        val hasRoute = plan.from.isNotBlank() && plan.toStops.any { it.isNotBlank() }

        Button(
            onClick = { openInGoogleMaps(context, plan) },
            enabled = hasRoute,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.Map, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (plan.roundTrip) "Open route in Google Maps (there & back)" else "Open route in Google Maps",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = {
                findingPitstops = true
                pitstopMessage = null
                scope.launch {
                    val orderedNames = listOf(plan.from) + plan.toStops
                    val breakValue = plan.breakEvery.toDoubleOrNull()
                    val pitstops = RouteRepository.suggestPitstops(
                        orderedPlaceNames = orderedNames,
                        breakEveryKm = if (plan.breakUnit == BreakUnit.KM) breakValue else null,
                        breakEveryHours = if (plan.breakUnit == BreakUnit.HOURS) breakValue else null
                    )
                    findingPitstops = false
                    if (pitstops.isEmpty()) {
                        pitstopMessage = "Couldn't find pitstops right now — check your internet connection, your places, and that a break amount is set."
                    } else {
                        addSuggestedStopsToItinerary(context, pitstops)
                        pitstopMessage = "Added ${pitstops.size} suggested stop${if (pitstops.size == 1) "" else "s"} to Day 1 of your Itinerary — edit them there any time."
                    }
                }
            },
            enabled = hasRoute && !findingPitstops && plan.breakEvery.toDoubleOrNull() != null,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (findingPitstops) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Finding pitstops…")
            } else {
                Text("Suggest pitstops for Itinerary")
            }
        }
        Text(
            "Uses free OpenStreetMap data to find real places roughly every \"break at every\" interval along your route.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        pitstopMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AutocompletePlaceField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                onValueChange(new)
                searchJob?.cancel()
                if (new.trim().length >= 2) {
                    searchJob = scope.launch {
                        delay(400) // debounce: wait for a pause in typing before calling the free search API
                        val results = PlacesRepository.searchPlaces(new)
                        suggestions = results
                        expanded = results.isNotEmpty()
                    }
                } else {
                    suggestions = emptyList()
                    expanded = false
                }
            },
            label = { Text(label) },
            placeholder = { Text("Type at least 2 letters for suggestions") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (expanded && suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column {
                    suggestions.take(5).forEachIndexed { i, s ->
                        Text(
                            s.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(s.displayName)
                                    expanded = false
                                }
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                        if (i < suggestions.take(5).lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakUnitOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectable(selected = selected, onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

@Composable
private fun RoutePrefOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectable(selected = selected, onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}

private fun openInGoogleMaps(context: Context, plan: RoutePlan) {
    val validStops = plan.toStops.map { it.trim() }.filter { it.isNotBlank() }
    if (plan.from.isBlank() || validStops.isEmpty()) return

    val origin = URLEncoder.encode(plan.from, "UTF-8")
    val destinationName = if (plan.roundTrip) plan.from else validStops.last()
    val destination = URLEncoder.encode(destinationName, "UTF-8")
    val waypointStops = if (plan.roundTrip) validStops else validStops.dropLast(1)
    val waypoints = waypointStops.joinToString("|") { URLEncoder.encode(it, "UTF-8") }

    val url = buildString {
        append("https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&travelmode=driving")
        if (waypoints.isNotBlank()) append("&waypoints=$waypoints")
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Couldn't open Google Maps — is it installed?", Toast.LENGTH_LONG).show()
    }
}

/** Appends the given pitstops as new, clearly-tagged rows to Day 1 of the saved itinerary
 *  (creating Day 1 if it doesn't exist yet). Existing rows are left untouched. */
private fun addSuggestedStopsToItinerary(context: Context, pitstops: List<RouteRepository.Pitstop>) {
    val days = LocalStore.loadItinerary(context).toMutableList()
    val dayIndex = days.indexOfFirst { it.label == "Day 1" }
    val day1 = if (dayIndex >= 0) days[dayIndex] else ItineraryDay(id = UUID.randomUUID().toString(), label = "Day 1")
    var nextOrder = (day1.stops.maxOfOrNull { it.order } ?: -1) + 1

    val newStops = pitstops.map { p ->
        val hours = (p.elapsedMinutes / 60).toInt()
        val minutes = (p.elapsedMinutes % 60).roundToInt()
        val elapsed = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        ItineraryStop(
            id = UUID.randomUUID().toString(),
            fromTime = "",
            tillTime = "",
            place = p.placeName,
            notes = "Suggested break • ~${p.distanceKm.roundToInt()} km / $elapsed from start",
            order = nextOrder++,
            isSuggested = true
        )
    }

    val updatedDay1 = day1.copy(stops = day1.stops + newStops)
    if (dayIndex >= 0) days[dayIndex] = updatedDay1 else days.add(0, updatedDay1)
    LocalStore.saveItinerary(context, days)
}
