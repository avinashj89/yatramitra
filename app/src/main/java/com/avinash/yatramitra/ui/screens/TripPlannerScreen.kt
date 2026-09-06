package com.avinash.yatramitra.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.PlacesRepository
import com.avinash.yatramitra.data.RouteRepository
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.model.PlaceSuggestion
import com.avinash.yatramitra.model.RoutePlan
import com.avinash.yatramitra.model.RoutePreference
import com.avinash.yatramitra.model.RouteSuggestion
import com.avinash.yatramitra.model.SuggestionStatus
import com.avinash.yatramitra.ui.components.InitialsAvatar
import com.avinash.yatramitra.ui.theme.Spacing
import com.avinash.yatramitra.ui.util.launchSafely
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPlannerScreen(
    session: LocalStore.Session,
    members: List<Member>,
    currentRole: MemberRole,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var plan by remember { mutableStateOf(RoutePlan()) }
    var loaded by remember { mutableStateOf(false) }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    var routeInfo by remember { mutableStateOf<RouteRepository.RouteInfo?>(null) }
    var routeLoading by remember { mutableStateOf(false) }

    var findingPitstops by remember { mutableStateOf(false) }
    var pitstopMessage by remember { mutableStateOf<String?>(null) }
    var computedPitstops by remember { mutableStateOf<List<RouteRepository.Pitstop>>(emptyList()) }

    var selectedPreferences by remember {
        mutableStateOf(setOf("Temples & Spiritual", "Dhabas & Highway Food", "Heritage & Forts"))
    }

    var suggestions by remember { mutableStateOf<List<RouteSuggestion>>(emptyList()) }
    var suggestionText by remember { mutableStateOf("") }

    LaunchedEffect(session.tripCode) {
        launch {
            TripRepository.observeRoutePlan(session.tripCode).collect { remote ->
                plan = remote
                loaded = true
            }
        }
        launch {
            TripRepository.observeRouteSuggestions(session.tripCode).collect { suggestions = it }
        }
    }

    fun updatePlan(new: RoutePlan) {
        plan = new
        saveJob?.cancel()
        saveJob = scope.launchSafely(onError, "Couldn't save your changes — check your internet connection.") {
            delay(400) // debounce: avoid a Firestore write on every keystroke
            TripRepository.updateRoutePlan(session.tripCode, new)
        }
    }

    LaunchedEffect(plan.from, plan.toStops) {
        val names = listOf(plan.from) + plan.toStops
        val validCount = names.count { it.trim().isNotBlank() }
        if (validCount < 2) {
            routeInfo = null
        } else {
            delay(500) // debounce: wait for a pause before hitting the free geocode/route APIs
            routeLoading = true
            routeInfo = RouteRepository.fetchRouteSummary(names)
            routeLoading = false
        }
    }

    if (!loaded) return

    val isOrganizer = currentRole == MemberRole.ORGANIZER
    val currentMemberName = members.find { it.id == session.memberId }?.name ?: session.memberName
    val hasRoute = plan.from.isNotBlank() && plan.toStops.any { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        RouteScreenHeader(isOrganizer = isOrganizer, tripName = plan.tripName, travelerCount = members.size)

        if (isOrganizer) {
            OrganizerRouteForm(plan = plan, onPlanChange = ::updatePlan)

            HorizontalDivider()

            PitstopEngineCard(
                plan = plan,
                onPlanChange = ::updatePlan,
                selectedPreferences = selectedPreferences,
                onTogglePreference = { pref ->
                    selectedPreferences = if (pref in selectedPreferences) {
                        selectedPreferences - pref
                    } else {
                        selectedPreferences + pref
                    }
                },
                computedPitstops = computedPitstops,
                findingPitstops = findingPitstops,
                enabled = hasRoute && plan.breakEvery.toDoubleOrNull() != null,
                onGeneratePitstops = {
                    findingPitstops = true
                    pitstopMessage = null
                    scope.launchSafely(
                        onError = {
                            findingPitstops = false
                            onError(it)
                        },
                        errorMessage = "Couldn't generate pitstops — check your internet connection and try again."
                    ) {
                        val orderedNames = listOf(plan.from) + plan.toStops
                        val breakValue = plan.breakEvery.toDoubleOrNull()
                        val pitstops = RouteRepository.suggestPitstops(
                            orderedPlaceNames = orderedNames,
                            breakEveryKm = if (plan.breakUnit == BreakUnit.KM) breakValue else null,
                            breakEveryHours = if (plan.breakUnit == BreakUnit.HOURS) breakValue else null
                        )
                        findingPitstops = false
                        computedPitstops = pitstops
                        pitstopMessage = if (pitstops.isEmpty()) {
                            "Couldn't find pitstops right now — check your internet connection, your places, and that a break amount is set."
                        } else {
                            TripRepository.regenerateDay1FromRoute(session.tripCode, plan, pitstops)
                            "Added ${pitstops.size} suggested stop${if (pitstops.size == 1) "" else "s"} to Day 1 of your Itinerary — edit them there any time."
                        }
                    }
                },
                pitstopMessage = pitstopMessage
            )
        } else {
            ReadOnlyRouteCard(plan = plan)
        }

        HorizontalDivider()

        RouteSummaryCard(
            loading = routeLoading,
            routeInfo = routeInfo,
            hasRoute = hasRoute,
            onOpenMaps = { openInGoogleMaps(context, plan, routeInfo, computedPitstops) }
        )

        if (isOrganizer) {
            HorizontalDivider()
            TripMembersCard(
                session = session,
                members = members,
                scope = scope,
                onError = onError
            )
        }

        HorizontalDivider()

        RouteSuggestionsCard(
            isOrganizer = isOrganizer,
            suggestions = suggestions,
            currentMemberId = session.memberId,
            suggestionText = suggestionText,
            onSuggestionTextChange = { suggestionText = it },
            onSubmit = {
                val text = suggestionText.trim()
                if (text.isNotBlank()) {
                    scope.launchSafely(onError, "Couldn't send your suggestion — check your internet connection.") {
                        TripRepository.addRouteSuggestion(session.tripCode, session.memberId, currentMemberName, text)
                        suggestionText = ""
                    }
                }
            },
            onAccept = { id ->
                scope.launchSafely(onError, "Couldn't accept that suggestion — check your internet connection.") {
                    TripRepository.updateRouteSuggestionStatus(session.tripCode, id, SuggestionStatus.ACCEPTED)
                }
            },
            onDismiss = { id ->
                scope.launchSafely(onError, "Couldn't dismiss that suggestion — check your internet connection.") {
                    TripRepository.updateRouteSuggestionStatus(session.tripCode, id, SuggestionStatus.DISMISSED)
                }
            }
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RouteScreenHeader(isOrganizer: Boolean, tripName: String, travelerCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(if (isOrganizer) "Organizer Mode" else "Group Member Mode") }
            )
            AssistChip(onClick = {}, enabled = false, label = { Text("Auto-saved") })
        }
        Text(
            tripName.ifBlank { "Plan your route" },
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "$travelerCount traveler${if (travelerCount == 1) "" else "s"} on this trip",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OrganizerRouteForm(plan: RoutePlan, onPlanChange: (RoutePlan) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = plan.tripName,
            onValueChange = { onPlanChange(plan.copy(tripName = it)) },
            label = { Text("Trip name") },
            placeholder = { Text("e.g. Coorg long weekend") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        AutocompletePlaceField(
            label = "From",
            value = plan.from,
            onValueChange = { onPlanChange(plan.copy(from = it)) }
        )

        plan.toStops.forEachIndexed { index, stopValue ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AutocompletePlaceField(
                    label = if (plan.toStops.size == 1) "To" else "To ${index + 1}",
                    value = stopValue,
                    onValueChange = { new ->
                        val updated = plan.toStops.toMutableList().also { it[index] = new }
                        onPlanChange(plan.copy(toStops = updated))
                    },
                    modifier = Modifier.weight(1f)
                )
                if (plan.toStops.size > 1) {
                    IconButton(onClick = {
                        val updated = plan.toStops.toMutableList().also { it.removeAt(index) }
                        onPlanChange(plan.copy(toStops = updated))
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove this stop")
                    }
                }
                if (index == plan.toStops.lastIndex && plan.toStops.size < RoutePlan.MAX_TO_STOPS) {
                    IconButton(onClick = { onPlanChange(plan.copy(toStops = plan.toStops + "")) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add another stop")
                    }
                }
            }
        }
        if (plan.toStops.size >= RoutePlan.MAX_TO_STOPS) {
            Text(
                "Up to ${RoutePlan.MAX_TO_STOPS} stops — that's the max for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.inverseSurface)
                .clickable { onPlanChange(plan.copy(roundTrip = !plan.roundTrip)) }
                .padding(Spacing.sm)
        ) {
            Checkbox(
                checked = plan.roundTrip,
                onCheckedChange = { onPlanChange(plan.copy(roundTrip = it)) },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.tertiary,
                    uncheckedColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            )
            Spacer(Modifier.width(4.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Loop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Round trip (return to starting point)",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyRouteCard(plan: RoutePlan) {
    val validStops = plan.toStops.map { it.trim() }.filter { it.isNotBlank() }
    ElevatedCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(plan.tripName.ifBlank { "This trip's route" }, style = MaterialTheme.typography.titleMedium)
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Locked by the Organizer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                "Only the Organizer can change the route. You can propose changes below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("From: ${plan.from.ifBlank { "Not set yet" }}", style = MaterialTheme.typography.bodyLarge)
            validStops.forEachIndexed { i, stop ->
                Text("Stop ${i + 1}: $stop", style = MaterialTheme.typography.bodyMedium)
            }
            if (plan.roundTrip) {
                Text(
                    "Round trip — returns to the starting point",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
private fun PitstopEngineCard(
    plan: RoutePlan,
    onPlanChange: (RoutePlan) -> Unit,
    selectedPreferences: Set<String>,
    onTogglePreference: (String) -> Unit,
    computedPitstops: List<RouteRepository.Pitstop>,
    findingPitstops: Boolean,
    enabled: Boolean,
    onGeneratePitstops: () -> Unit,
    pitstopMessage: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Smart Pitstop Engine", style = MaterialTheme.typography.titleMedium)
        }

        ElevatedCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Break calculation mode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BreakUnitOption(
                        label = "By kilometers (km)",
                        selected = plan.breakUnit == BreakUnit.KM,
                        onSelect = { onPlanChange(plan.copy(breakUnit = BreakUnit.KM)) }
                    )
                    BreakUnitOption(
                        label = "By hours (hrs)",
                        selected = plan.breakUnit == BreakUnit.HOURS,
                        onSelect = { onPlanChange(plan.copy(breakUnit = BreakUnit.HOURS)) }
                    )
                }

                Text(
                    "Stop frequency",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val presets = if (plan.breakUnit == BreakUnit.KM) listOf(80, 100, 150) else listOf(1, 2, 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    presets.forEach { preset ->
                        val label = if (plan.breakUnit == BreakUnit.KM) "Every $preset km" else "Every $preset hr"
                        val selected = plan.breakEvery == preset.toString()
                        FilterChip(
                            selected = selected,
                            onClick = { onPlanChange(plan.copy(breakEvery = preset.toString())) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = plan.breakEvery,
                    onValueChange = { new -> if (new.all { it.isDigit() }) onPlanChange(plan.copy(breakEvery = new)) },
                    label = { Text("Or type a custom number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(220.dp)
                )

                Text(
                    "Group pitstop preferences",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRowPreferences(selectedPreferences = selectedPreferences, onToggle = onTogglePreference)

                Text(
                    "Route preference",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoutePrefOption(
                        label = "Fastest route",
                        selected = plan.routePreference == RoutePreference.FASTEST,
                        onSelect = { onPlanChange(plan.copy(routePreference = RoutePreference.FASTEST)) }
                    )
                    RoutePrefOption(
                        label = "Surprise me",
                        selected = plan.routePreference == RoutePreference.SURPRISE,
                        onSelect = { onPlanChange(plan.copy(routePreference = RoutePreference.SURPRISE)) }
                    )
                }

                Button(
                    onClick = onGeneratePitstops,
                    enabled = enabled && !findingPitstops,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (findingPitstops) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Finding pitstops…")
                    } else {
                        Text("Generate pitstops for Itinerary")
                    }
                }
                pitstopMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                if (computedPitstops.isNotEmpty()) {
                    Text(
                        "Generated route pitstops",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    computedPitstops.forEachIndexed { i, stop ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(Spacing.xs)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${i + 1}",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(stop.placeName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "~${stop.distanceKm.roundToInt()} km from start",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowRowPreferences(selectedPreferences: Set<String>, onToggle: (String) -> Unit) {
    data class Pref(val label: String, val icon: ImageVector)
    val options = listOf(
        Pref("Temples & Spiritual", Icons.Filled.AccountBalance),
        Pref("Dhabas & Highway Food", Icons.Filled.Restaurant),
        Pref("Heritage & Forts", Icons.Filled.LocationCity),
        Pref("EV Charging / Fuel", Icons.Filled.LocalGasStation),
        Pref("Scenic Viewpoints", Icons.Filled.Landscape)
    )
    // Two rows of chips (FlowRow isn't in this project's Compose foundation version), wrapped manually.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { pref ->
                    FilterChip(
                        selected = pref.label in selectedPreferences,
                        onClick = { onToggle(pref.label) },
                        leadingIcon = { Icon(pref.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(pref.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSummaryCard(
    loading: Boolean,
    routeInfo: RouteRepository.RouteInfo?,
    hasRoute: Boolean,
    onOpenMaps: () -> Unit
) {
    ElevatedCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Route summary", style = MaterialTheme.typography.titleMedium)
            }
            when {
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Calculating route…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                routeInfo != null -> {
                    val km = (routeInfo.distanceMeters / 1000.0).roundToInt()
                    val hours = (routeInfo.durationSeconds / 3600).toInt()
                    val minutes = ((routeInfo.durationSeconds % 3600) / 60).toInt()
                    Text(
                        "$km km • ${if (hours > 0) "${hours}h " else ""}${minutes}m",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Free route estimate via OpenStreetMap/OSRM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                hasRoute -> Text(
                    "Couldn't calculate a route right now — check your internet connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> Text(
                    "Fill in From and at least one destination to see distance and duration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onOpenMaps,
                enabled = hasRoute,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.Map, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open in Google Maps", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TripMembersCard(
    session: LocalStore.Session,
    members: List<Member>,
    scope: kotlinx.coroutines.CoroutineScope,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showAddPeople by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Trip members (${members.size})", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { showAddPeople = true }) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        ElevatedCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                members.forEach { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InitialsAvatar(name = member.name, size = 36.dp)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(member.name, modifier = Modifier.weight(1f))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    if (member.role == MemberRole.ORGANIZER) "Organizer" else "Group Member",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Trip code: ${session.tripCode}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { clipboard.setText(AnnotatedString(session.tripCode)) }) {
                        Text("Copy code")
                    }
                }
            }
        }
    }

    if (showAddPeople) {
        AlertDialog(
            onDismissRequest = { showAddPeople = false },
            title = { Text("Add a travel companion") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotBlank()) {
                            scope.launchSafely(onError, "Couldn't add that person — check your internet connection.") {
                                TripRepository.joinTrip(session.tripCode, name, role = MemberRole.JOINER)
                            }
                        }
                        newName = ""
                        showAddPeople = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPeople = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RouteSuggestionsCard(
    isOrganizer: Boolean,
    suggestions: List<RouteSuggestion>,
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
                if (isOrganizer) "Group Member change suggestions" else "Suggest a change to the Organizer",
                style = MaterialTheme.typography.titleMedium
            )
        }

        ElevatedCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = suggestionText,
                    onValueChange = onSuggestionTextChange,
                    placeholder = { Text("Suggest a route change or stop to discuss with the group…") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onSubmit, enabled = suggestionText.isNotBlank()) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isOrganizer) "Post route note" else "Send suggestion")
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
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RoutePrefOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.selectable(selected = selected, onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private data class MapWaypoint(val name: String, val cumulativeKm: Double)

/** Opens Google Maps with turn-by-turn directions through the full route: the manually-typed
 *  stops *and* any pitstops generated by the Smart Pitstop Engine, merged into one waypoint list
 *  in true route order (not just typed stops first, pitstops tacked on after). Pitstops already
 *  carry their distance from the start of the route; named stops' distances come from OSRM's
 *  per-leg distances on the last-fetched [routeInfo], so both sides are on the same scale. */
private fun openInGoogleMaps(
    context: Context,
    plan: RoutePlan,
    routeInfo: RouteRepository.RouteInfo?,
    pitstops: List<RouteRepository.Pitstop>
) {
    val validStops = plan.toStops.map { it.trim() }.filter { it.isNotBlank() }
    if (plan.from.isBlank() || validStops.isEmpty()) return

    val namedChain = listOf(plan.from) + validStops
    val cumulativeKm = mutableListOf(0.0)
    if (routeInfo != null && routeInfo.legDistancesMeters.size >= namedChain.size - 1) {
        var running = 0.0
        for (i in 1 until namedChain.size) {
            running += routeInfo.legDistancesMeters[i - 1] / 1000.0
            cumulativeKm.add(running)
        }
    } else {
        // No route fetched yet (e.g. offline) — fall back to typed order, pitstops omitted since
        // we have no shared distance scale to place them on.
        for (i in 1 until namedChain.size) cumulativeKm.add(i.toDouble())
    }

    val lastNamed = MapWaypoint(namedChain.last(), cumulativeKm.last())
    val intermediateNamed = namedChain.drop(1).dropLast(1)
        .mapIndexed { i, name -> MapWaypoint(name, cumulativeKm[i + 1]) }
    val pitstopPoints = if (routeInfo != null) pitstops.map { MapWaypoint(it.placeName, it.distanceKm) } else emptyList()

    val beforeDestination = (intermediateNamed + pitstopPoints).sortedBy { it.cumulativeKm }
    val waypointNames = if (plan.roundTrip) (beforeDestination + lastNamed).map { it.name } else beforeDestination.map { it.name }

    val origin = URLEncoder.encode(plan.from, "UTF-8")
    val destinationName = if (plan.roundTrip) plan.from else lastNamed.name
    val destination = URLEncoder.encode(destinationName, "UTF-8")
    val waypoints = waypointNames.joinToString("|") { URLEncoder.encode(it, "UTF-8") }

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
