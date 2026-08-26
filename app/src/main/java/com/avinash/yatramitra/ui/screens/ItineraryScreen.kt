package com.avinash.yatramitra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.model.ItineraryDay
import com.avinash.yatramitra.model.ItineraryStop
import com.avinash.yatramitra.ui.theme.AmberAccent
import java.util.UUID

@Composable
fun ItineraryScreen() {
    val context = LocalContext.current
    var days by remember { mutableStateOf<List<ItineraryDay>>(emptyList()) }
    var tripName by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        days = LocalStore.loadItinerary(context)
        tripName = LocalStore.loadRoutePlan(context).tripName
        loaded = true
    }

    fun persist(newDays: List<ItineraryDay>) {
        days = newDays
        LocalStore.saveItinerary(context, newDays)
    }

    var editingDayId by remember { mutableStateOf<String?>(null) }
    var editingStop by remember { mutableStateOf<ItineraryStop?>(null) }
    var showStopDialog by remember { mutableStateOf(false) }

    if (!loaded) return

    Column(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)) {
            Text("Itinerary", style = MaterialTheme.typography.titleLarge)
            if (tripName.isNotBlank()) {
                Text(
                    "Planning: $tripName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                "Build your day-by-day plan: meetup points, meals, sightseeing stops — whatever you like.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(days, key = { it.id }) { day ->
                DayCard(
                    day = day,
                    onAddStop = {
                        editingDayId = day.id
                        editingStop = null
                        showStopDialog = true
                    },
                    onEditStop = { stop ->
                        editingDayId = day.id
                        editingStop = stop
                        showStopDialog = true
                    },
                    onDeleteStop = { stop ->
                        persist(days.map {
                            if (it.id == day.id) it.copy(stops = it.stops.filterNot { s -> s.id == stop.id })
                            else it
                        })
                    },
                    onDeleteDay = {
                        persist(days.filterNot { it.id == day.id })
                    }
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        val newDay = ItineraryDay(
                            id = UUID.randomUUID().toString(),
                            label = "Day ${days.size + 1}"
                        )
                        persist(days + newDay)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add day")
                }
            }
        }
    }

    if (showStopDialog && editingDayId != null) {
        StopEditDialog(
            initial = editingStop,
            onDismiss = { showStopDialog = false },
            onSave = { stop ->
                persist(days.map { d ->
                    if (d.id != editingDayId) return@map d
                    val existingIndex = d.stops.indexOfFirst { it.id == stop.id }
                    val newStops = if (existingIndex >= 0) {
                        d.stops.toMutableList().apply { set(existingIndex, stop) }
                    } else {
                        d.stops + stop.copy(order = d.stops.size)
                    }
                    d.copy(stops = newStops)
                })
                showStopDialog = false
            }
        )
    }
}

@Composable
private fun DayCard(
    day: ItineraryDay,
    onAddStop: () -> Unit,
    onEditStop: (ItineraryStop) -> Unit,
    onDeleteStop: (ItineraryStop) -> Unit,
    onDeleteDay: () -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AmberAccent.copy(alpha = 0.35f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(day.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDeleteDay) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete day")
                }
            }

            if (day.stops.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text("From", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.8f))
                    Text("Till", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.8f))
                    Text("Stop / notes", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.6f))
                    Spacer(Modifier.width(40.dp))
                }
                HorizontalDivider()
            }

            day.stops.sortedBy { it.order }.forEach { stop ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stop.fromTime, modifier = Modifier.weight(0.8f))
                    Text(stop.tillTime, modifier = Modifier.weight(0.8f))
                    Column(modifier = Modifier.weight(1.6f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stop.place, fontWeight = FontWeight.Medium)
                            if (stop.isSuggested) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Suggested",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (stop.notes.isNotBlank()) {
                            Text(
                                stop.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF00796B)
                            )
                        }
                    }
                    IconButton(onClick = { onEditStop(stop) }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onDeleteStop(stop) }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider()
            }

            TextButton(onClick = onAddStop, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add stop")
            }
        }
    }
}

@Composable
private fun StopEditDialog(
    initial: ItineraryStop?,
    onDismiss: () -> Unit,
    onSave: (ItineraryStop) -> Unit
) {
    var fromTime by remember { mutableStateOf(initial?.fromTime ?: "") }
    var tillTime by remember { mutableStateOf(initial?.tillTime ?: "") }
    var place by remember { mutableStateOf(initial?.place ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add stop" else "Edit stop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = fromTime,
                        onValueChange = { fromTime = it },
                        label = { Text("From") },
                        placeholder = { Text("05:00 AM") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tillTime,
                        onValueChange = { tillTime = it },
                        label = { Text("Till") },
                        placeholder = { Text("07:30 AM") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Place / stop name") },
                    placeholder = { Text("Paakashala Yediyur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Breakfast") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        ItineraryStop(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            fromTime = fromTime,
                            tillTime = tillTime,
                            place = place,
                            notes = notes,
                            order = initial?.order ?: 0,
                            isSuggested = initial?.isSuggested ?: false
                        )
                    )
                },
                enabled = place.isNotBlank() || fromTime.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
