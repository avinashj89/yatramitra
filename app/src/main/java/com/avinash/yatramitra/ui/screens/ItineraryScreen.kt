package com.avinash.yatramitra.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.model.ItineraryDay
import com.avinash.yatramitra.model.ItinerarySuggestion
import com.avinash.yatramitra.model.ItineraryStop
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.model.StopSource
import com.avinash.yatramitra.model.SuggestionStatus
import com.avinash.yatramitra.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ItineraryScreen(
    session: LocalStore.Session,
    members: List<Member>,
    currentRole: MemberRole
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var days by remember { mutableStateOf<List<ItineraryDay>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selectedDayId by remember { mutableStateOf<String?>(null) }

    var suggestions by remember { mutableStateOf<List<ItinerarySuggestion>>(emptyList()) }
    var suggestionText by remember { mutableStateOf("") }

    var editingStop by remember { mutableStateOf<ItineraryStop?>(null) }
    var showStopDialog by remember { mutableStateOf(false) }

    LaunchedEffect(session.tripCode) {
        launch {
            TripRepository.observeItineraryDays(session.tripCode).collect { remote ->
                days = remote
                if (selectedDayId == null || remote.none { it.id == selectedDayId }) {
                    selectedDayId = remote.minByOrNull { it.order }?.id
                }
                loaded = true
            }
        }
        launch {
            TripRepository.observeItinerarySuggestions(session.tripCode).collect { suggestions = it }
        }
    }

    if (!loaded) return

    val isOrganizer = currentRole == MemberRole.ORGANIZER
    val currentMemberName = members.find { it.id == session.memberId }?.name ?: session.memberName
    val sortedDays = days.sortedBy { it.order }
    val selectedDay = sortedDays.find { it.id == selectedDayId }

    fun persistDay(day: ItineraryDay) {
        scope.launch { TripRepository.saveItineraryDay(session.tripCode, day) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Itinerary", style = MaterialTheme.typography.titleLarge)
            if (isOrganizer) {
                Text(
                    "Build your day-by-day plan: meetup points, meals, sightseeing stops — whatever you like.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                JoinerReadOnlyBanner()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sortedDays.forEach { day ->
                FilterChip(
                    selected = day.id == selectedDayId,
                    onClick = { selectedDayId = day.id },
                    label = { Text(day.label) }
                )
            }
            if (isOrganizer) {
                AssistChip(
                    onClick = {
                        val newDay = ItineraryDay(
                            id = UUID.randomUUID().toString(),
                            label = "Day ${days.size + 1}",
                            order = days.size
                        )
                        persistDay(newDay)
                        selectedDayId = newDay.id
                    },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Add day") }
                )
            }
        }

        Spacer(Modifier.height(Spacing.xs))

        if (selectedDay == null) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    if (isOrganizer) "Tap \"Add day\" to start your itinerary." else "The Organizer hasn't added any days yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                item {
                    DayHeaderRow(
                        day = selectedDay,
                        isOrganizer = isOrganizer,
                        onDelete = {
                            scope.launch { TripRepository.deleteItineraryDay(session.tripCode, selectedDay.id) }
                        }
                    )
                }

                items(selectedDay.stops.sortedBy { it.order }, key = { it.id }) { stop ->
                    StopRow(
                        stop = stop,
                        canEdit = isOrganizer,
                        onEdit = {
                            editingStop = stop
                            showStopDialog = true
                        },
                        onDelete = {
                            persistDay(selectedDay.copy(stops = selectedDay.stops.filterNot { it.id == stop.id }))
                        }
                    )
                }

                if (isOrganizer) {
                    item {
                        TextButton(onClick = {
                            editingStop = null
                            showStopDialog = true
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add stop")
                        }
                    }
                    item {
                        Button(
                            onClick = { shareItineraryDay(context, selectedDay) },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save & notify group", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                item {
                    ItinerarySuggestionsCard(
                        isOrganizer = isOrganizer,
                        suggestions = suggestions,
                        currentMemberId = session.memberId,
                        suggestionText = suggestionText,
                        onSuggestionTextChange = { suggestionText = it },
                        onSubmit = {
                            val text = suggestionText.trim()
                            if (text.isNotBlank()) {
                                scope.launch {
                                    TripRepository.addItinerarySuggestion(session.tripCode, session.memberId, currentMemberName, text)
                                    suggestionText = ""
                                }
                            }
                        },
                        onAccept = { id ->
                            scope.launch { TripRepository.updateItinerarySuggestionStatus(session.tripCode, id, SuggestionStatus.ACCEPTED) }
                        },
                        onDismiss = { id ->
                            scope.launch { TripRepository.updateItinerarySuggestionStatus(session.tripCode, id, SuggestionStatus.DISMISSED) }
                        }
                    )
                }
            }
        }
    }

    if (showStopDialog && selectedDay != null) {
        StopEditDialog(
            initial = editingStop,
            onDismiss = { showStopDialog = false },
            onSave = { stop ->
                val existingIndex = selectedDay.stops.indexOfFirst { it.id == stop.id }
                val newStops = if (existingIndex >= 0) {
                    selectedDay.stops.toMutableList().apply { set(existingIndex, stop) }
                } else {
                    selectedDay.stops + stop.copy(order = selectedDay.stops.size)
                }
                persistDay(selectedDay.copy(stops = newStops))
                showStopDialog = false
            }
        )
    }
}

@Composable
private fun JoinerReadOnlyBanner() {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(Spacing.sm)
    ) {
        Icon(
            Icons.Filled.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                "Role: Joiner (read-only)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Only the Organizer can edit the itinerary. Suggest changes below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayHeaderRow(day: ItineraryDay, isOrganizer: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(day.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        if (isOrganizer) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete day")
            }
        }
    }
}

@Composable
private fun StopRow(stop: ItineraryStop, canEdit: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(0.8f)) {
            Text(stop.fromTime, style = MaterialTheme.typography.bodyMedium)
            if (stop.tillTime.isNotBlank()) {
                Text(stop.tillTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stop.place.ifBlank { "Untitled stop" }, fontWeight = FontWeight.Medium)
                if (stop.source != StopSource.MANUAL) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Locked from the Route tab",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (stop.source != StopSource.MANUAL) {
                Text(
                    when (stop.source) {
                        StopSource.ROUTE_START -> "Route start"
                        StopSource.ROUTE_PITSTOP -> "Prepopulated from Route tab"
                        StopSource.ROUTE_END -> "Route destination"
                        StopSource.MANUAL -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (stop.notes.isNotBlank()) {
                Text(stop.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        if (canEdit) {
            IconButton(onClick = onEdit, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ItinerarySuggestionsCard(
    isOrganizer: Boolean,
    suggestions: List<ItinerarySuggestion>,
    currentMemberId: String,
    suggestionText: String,
    onSuggestionTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAccept: (String) -> Unit,
    onDismiss: (String) -> Unit
) {
    val visibleSuggestions = if (isOrganizer) suggestions else suggestions.filter { it.authorMemberId == currentMemberId }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.QuestionAnswer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                if (isOrganizer) "Joiner schedule suggestions" else "Suggest a timing or activity change",
                style = MaterialTheme.typography.titleMedium
            )
        }

        ElevatedCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = suggestionText,
                    onValueChange = onSuggestionTextChange,
                    placeholder = { Text("Suggest a timing or activity adjustment…") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onSubmit, enabled = suggestionText.isNotBlank()) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Submit suggestion")
                    }
                }
            }
        }

        if (visibleSuggestions.isEmpty()) {
            Text(
                if (isOrganizer) "No suggestions yet." else "Your suggestions to the Organizer will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        visibleSuggestions.forEach { suggestion ->
            ElevatedCard {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(suggestion.authorName, fontWeight = FontWeight.Bold)
                        Text(
                            when (suggestion.status) {
                                SuggestionStatus.PENDING -> "Pending"
                                SuggestionStatus.ACCEPTED -> "Accepted"
                                SuggestionStatus.DISMISSED -> "Dismissed"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (suggestion.status) {
                                SuggestionStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
                                SuggestionStatus.ACCEPTED -> MaterialTheme.colorScheme.tertiary
                                SuggestionStatus.DISMISSED -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    Text(suggestion.text, style = MaterialTheme.typography.bodyMedium)
                    if (isOrganizer && suggestion.status == SuggestionStatus.PENDING) {
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { onDismiss(suggestion.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Dismiss")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onAccept(suggestion.id) }) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Accept")
                            }
                        }
                    }
                }
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
    val placeLocked = initial != null && initial.source != StopSource.MANUAL

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
                    enabled = !placeLocked,
                    modifier = Modifier.fillMaxWidth()
                )
                if (placeLocked) {
                    Text(
                        "Locked — this stop comes from the Route tab. Change it there instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                            source = initial?.source ?: StopSource.MANUAL
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

/** Shares a text summary of the day's plan — targets WhatsApp directly if installed, otherwise
 *  falls back to the normal share sheet. Firestore already syncs the data live to every open app;
 *  this is the "notify the group" step on top of that. */
private fun shareItineraryDay(context: Context, day: ItineraryDay) {
    val summary = buildString {
        appendLine("${day.label} itinerary:")
        day.stops.sortedBy { it.order }.forEach { stop ->
            val time = listOf(stop.fromTime, stop.tillTime).filter { it.isNotBlank() }.joinToString(" – ")
            append("• ")
            if (time.isNotBlank()) append("$time ")
            append(stop.place.ifBlank { "Untitled stop" })
            if (stop.notes.isNotBlank()) append(" (${stop.notes})")
            appendLine()
        }
    }
    val whatsapp = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, summary)
        setPackage("com.whatsapp")
    }
    try {
        context.startActivity(whatsapp)
    } catch (e: ActivityNotFoundException) {
        val generic = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        context.startActivity(Intent.createChooser(generic, "Share itinerary"))
    }
}
