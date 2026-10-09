package com.avinash.yatramitra.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.CurrentLocation
import com.avinash.yatramitra.data.PlaceRules
import com.avinash.yatramitra.data.PlaceSearch
import com.avinash.yatramitra.data.RoutePlans
import com.avinash.yatramitra.data.RouteRepository
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.data.TripRules
import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.PlaceSearchResult
import com.avinash.yatramitra.model.PlaceSource
import com.avinash.yatramitra.model.RouteDay
import com.avinash.yatramitra.model.RoutePlan
import com.avinash.yatramitra.model.RoutePreference
import com.avinash.yatramitra.model.TripStatus
import com.avinash.yatramitra.ui.components.InitialsAvatar
import com.avinash.yatramitra.ui.theme.Spacing
import com.avinash.yatramitra.ui.util.launchSafely
import kotlinx.coroutines.CancellationException
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
    isReadOnly: Boolean,
    groupName: String,
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
    // Generated pitstops per route day (key = day index), shown under the engine and sent to Maps.
    var computedPitstops by remember { mutableStateOf<Map<Int, List<RouteRepository.Pitstop>>>(emptyMap()) }

    var selectedDayIndex by rememberSaveable { mutableStateOf(0) }
    var dayPendingRemoval by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(session.tripCode) {
        TripRepository.observeRoutePlan(session.tripCode).collect { remote ->
            plan = remote
            loaded = true
        }
    }

    // The newest edit that hasn't been handed to Firestore yet (saves wait for a pause in typing).
    var unsavedPlan by remember { mutableStateOf<RoutePlan?>(null) }

    fun updatePlan(new: RoutePlan) {
        plan = new
        unsavedPlan = new
        saveJob?.cancel()
        saveJob = scope.launchSafely(onError, "Couldn't save your changes — check your internet connection.") {
            delay(400) // debounce: avoid a Firestore write on every keystroke
            unsavedPlan = null
            TripRepository.updateRoutePlan(session.tripCode, new)
        }
    }

    // Leaving the tab (or the trip) within that pause used to throw the last edit away, because the
    // waiting save was cancelled along with the screen.
    val latestUnsaved by rememberUpdatedState(unsavedPlan)
    DisposableEffect(session.tripCode) {
        onDispose { latestUnsaved?.let { TripRepository.saveRoutePlanNow(session.tripCode, it) } }
    }

    // The day being viewed or edited (Day 1 = index 0); kept in range if a day is removed elsewhere.
    val activeIndex = selectedDayIndex.coerceIn(0, (plan.days.size - 1).coerceAtLeast(0))
    val activeDay = plan.days.getOrElse(activeIndex) { RouteDay() }
    val dayNumber = activeIndex + 1

    LaunchedEffect(activeDay.from, activeDay.toStops, activeDay.roundTrip) {
        // Cleared straight away so nothing (distance, hospitals) uses the previous day's or the
        // previous places' route while the new one is worked out.
        routeInfo = null
        val places = RoutePlans.placesInOrder(activeDay)
        if (places.size < 2) {
            routeInfo = null
        } else {
            delay(500) // debounce: wait for a pause before hitting the free geocode/route APIs
            routeLoading = true
            routeInfo = RouteRepository.fetchRouteSummary(places)
            routeLoading = false
        }
    }
    LaunchedEffect(activeIndex) { pitstopMessage = null }

    if (!loaded) return

    val isOrganizer = currentRole == MemberRole.ORGANIZER
    val canEdit = TripRules.canEditPlan(currentRole, if (isReadOnly) TripStatus.COMPLETED else TripStatus.ONGOING)
    val hasRoute = RoutePlans.hasRoute(activeDay)
    val dayPitstops = computedPitstops[activeIndex].orEmpty()

    /** Writes this day's route (and its pitstops, if any) into the matching Itinerary day. */
    fun sendDayToItinerary(withPitstops: Boolean) {
        val index = activeIndex
        val day = activeDay
        findingPitstops = true
        pitstopMessage = null
        scope.launchSafely(
            onError = {
                findingPitstops = false
                onError(it)
            },
            errorMessage = if (withPitstops) {
                "Couldn't generate pitstops — check your internet connection and try again."
            } else {
                "Couldn't update the Itinerary — check your internet connection and try again."
            }
        ) {
            try {
                val pitstops = if (withPitstops) {
                    val breakValue = plan.breakEvery.toDoubleOrNull()
                    RouteRepository.suggestPitstops(
                        orderedPlaces = RoutePlans.placesInOrder(day),
                        breakEveryKm = if (plan.breakUnit == BreakUnit.KM) breakValue else null,
                        breakEveryHours = if (plan.breakUnit == BreakUnit.HOURS) breakValue else null,
                        categories = plan.pitstopCategories
                    )
                } else {
                    emptyList()
                }
                computedPitstops = computedPitstops + (index to pitstops)
                TripRepository.regenerateItineraryDayFromRoute(session.tripCode, index, day, pitstops)
                pitstopMessage = if (withPitstops) {
                    val intervalDescription = "every ${plan.breakEvery} ${if (plan.breakUnit == BreakUnit.KM) "km" else "hr"}"
                    val categoriesDescription = plan.pitstopCategories.ifEmpty { setOf("general amenities") }.joinToString(", ")
                    "Added ${pitstops.size} suggested stop${if (pitstops.size == 1) "" else "s"} to Day ${index + 1} of your Itinerary — edit them there any time." +
                        "\nSearched $intervalDescription for: $categoriesDescription"
                } else {
                    "Day ${index + 1}'s start and destination are now in the Itinerary."
                }
            } catch (e: RouteRepository.PitstopUnavailableException) {
                computedPitstops = computedPitstops - index
                pitstopMessage = e.message
            } finally {
                findingPitstops = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        RouteScreenHeader(isOrganizer = isOrganizer, tripName = groupName, travelerCount = members.size)

        RouteDaySelector(
            days = plan.days,
            selectedIndex = activeIndex,
            canEdit = canEdit,
            onSelect = { selectedDayIndex = it },
            onAddDay = {
                val newIndex = plan.days.size
                updatePlan(RoutePlans.addDay(plan))
                selectedDayIndex = newIndex
            }
        )

        if (canEdit) {
            OrganizerRouteForm(
                dayNumber = dayNumber,
                day = activeDay,
                onDayChange = { updatePlan(RoutePlans.updateDay(plan, activeIndex, it)) },
                canRemoveDay = plan.days.size > 1,
                onRemoveDay = { dayPendingRemoval = activeIndex },
                onMessage = onError
            )

            HorizontalDivider()

            PitstopEngineCard(
                plan = plan,
                onPlanChange = ::updatePlan,
                dayNumber = dayNumber,
                dayPitstopsOn = activeDay.pitstopsEnabled,
                onDayPitstopsChange = { on -> updatePlan(RoutePlans.updateDay(plan, activeIndex, activeDay.copy(pitstopsEnabled = on))) },
                totalDays = plan.days.size,
                computedPitstops = dayPitstops,
                findingPitstops = findingPitstops,
                enabled = activeDay.pitstopsEnabled && hasRoute && plan.breakEvery.toDoubleOrNull() != null,
                hasRoute = hasRoute,
                onGeneratePitstops = { sendDayToItinerary(withPitstops = true) },
                onAddToItinerary = { sendDayToItinerary(withPitstops = false) },
                pitstopMessage = pitstopMessage
            )
        } else {
            ReadOnlyRouteCard(
                day = activeDay,
                dayNumber = dayNumber,
                totalDays = plan.days.size,
                groupName = groupName,
                tripCompleted = isReadOnly
            )
        }

        HorizontalDivider()

        RouteSummaryCard(
            dayNumber = dayNumber,
            totalDays = plan.days.size,
            loading = routeLoading,
            routeInfo = routeInfo,
            hasRoute = hasRoute,
            onOpenMaps = { openInGoogleMaps(context, activeDay, routeInfo, dayPitstops) }
        )

        HorizontalDivider()

        HospitalsCard(
            tripCode = session.tripCode,
            days = plan.days,
            dayIndex = activeIndex,
            routeInfo = routeInfo,
            canUpdate = !isReadOnly
        )

        if (canEdit) {
            HorizontalDivider()
            TripMembersCard(
                session = session,
                members = members,
                groupName = groupName,
                scope = scope,
                onError = onError
            )
        }

        HorizontalDivider()

        // Ideas for the route now go straight into the Group chat tab (no accept/dismiss step).
        GroupChatHint("Want a different stop or route? Post it in the Group chat tab — everyone on the trip gets it.")

        Spacer(Modifier.height(24.dp))
    }

    dayPendingRemoval?.let { index ->
        AlertDialog(
            onDismissRequest = { dayPendingRemoval = null },
            title = { Text("Remove Day ${index + 1}?") },
            text = {
                Text(
                    "This removes Day ${index + 1}'s From and To places from the route. Later days move up " +
                        "one. The Itinerary isn't changed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dayPendingRemoval = null
                    updatePlan(RoutePlans.removeDay(plan, index))
                    computedPitstops = emptyMap() // day numbers shifted, so these no longer line up
                    selectedDayIndex = (index - 1).coerceAtLeast(0)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dayPendingRemoval = null }) { Text("Cancel") } }
        )
    }
}

/** Day 1, Day 2, ... chips across the top of the Route tab, plus "Add day" for the Organizer. */
@Composable
private fun RouteDaySelector(
    days: List<RouteDay>,
    selectedIndex: Int,
    canEdit: Boolean,
    onSelect: (Int) -> Unit,
    onAddDay: () -> Unit
) {
    if (days.size <= 1 && !canEdit) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEachIndexed { i, _ ->
                FilterChip(
                    selected = i == selectedIndex,
                    onClick = { onSelect(i) },
                    label = { Text("Day ${i + 1}") }
                )
            }
            if (canEdit && days.size < RoutePlan.MAX_DAYS) {
                AssistChip(
                    onClick = onAddDay,
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Add day") }
                )
            }
        }
        if (canEdit && days.size == 1) {
            Text(
                "Trip longer than a day? Tap \"Add day\" to plan each day's drive separately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
private fun OrganizerRouteForm(
    dayNumber: Int,
    day: RouteDay,
    onDayChange: (RouteDay) -> Unit,
    canRemoveDay: Boolean,
    onRemoveDay: () -> Unit,
    onMessage: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Day $dayNumber route", style = MaterialTheme.typography.titleMedium)
            if (canRemoveDay) {
                TextButton(onClick = onRemoveDay) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove Day $dayNumber")
                }
            }
        }

        // key(dayNumber): each day gets its own fields, so switching days never shows the previous
        // day's half-typed text or place suggestions.
        key(dayNumber) {
            AutocompletePlaceField(
                label = "From",
                value = day.from,
                onValueChange = { onDayChange(day.copy(from = it)) },
                onMessage = onMessage,
                allowCurrentLocation = true
            )

            day.toStops.forEachIndexed { index, stopValue ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    AutocompletePlaceField(
                        label = if (day.toStops.size == 1) "To" else "To ${index + 1}",
                        value = stopValue,
                        onValueChange = { new ->
                            val updated = day.toStops.toMutableList().also { it[index] = new }
                            onDayChange(day.copy(toStops = updated))
                        },
                        onMessage = onMessage,
                        near = day.from,
                        modifier = Modifier.weight(1f)
                    )
                    if (day.toStops.size > 1) {
                        IconButton(onClick = {
                            val updated = day.toStops.toMutableList().also { it.removeAt(index) }
                            onDayChange(day.copy(toStops = updated))
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove this stop")
                        }
                    }
                    if (index == day.toStops.lastIndex && day.toStops.size < RoutePlan.MAX_TO_STOPS) {
                        IconButton(onClick = { onDayChange(day.copy(toStops = day.toStops + Place())) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add another stop")
                        }
                    }
                }
            }
        }
        if (day.toStops.size >= RoutePlan.MAX_TO_STOPS) {
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
                .clickable { onDayChange(day.copy(roundTrip = !day.roundTrip)) }
                .padding(Spacing.sm)
        ) {
            Checkbox(
                checked = day.roundTrip,
                onCheckedChange = { onDayChange(day.copy(roundTrip = it)) },
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
                        "Round trip (end Day $dayNumber back at its starting point)",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyRouteCard(day: RouteDay, dayNumber: Int, totalDays: Int, groupName: String, tripCompleted: Boolean) {
    val validStops = day.toStops.filter { it.name.isNotBlank() }
    ElevatedCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (totalDays > 1) "Day $dayNumber of $totalDays" else groupName.ifBlank { "This trip's route" },
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Locked by the Organizer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                if (tripCompleted) {
                    "This trip is marked completed, so the route is locked. The Organizer can reopen it from the ⋮ menu."
                } else {
                    "Only the Organizer can change the route. You can suggest changes below."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RoutePlaceLine("From", day.from.name.ifBlank { "Not set yet" }, day.from.address, MaterialTheme.typography.bodyLarge)
            validStops.forEachIndexed { i, stop ->
                RoutePlaceLine("Stop ${i + 1}", stop.name, stop.address, MaterialTheme.typography.bodyMedium)
            }
            if (day.roundTrip) {
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
private fun RoutePlaceLine(label: String, name: String, address: String, style: TextStyle) {
    Column {
        Text("$label: $name", style = style)
        if (address.isNotBlank()) {
            Text(
                address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PitstopEngineCard(
    plan: RoutePlan,
    onPlanChange: (RoutePlan) -> Unit,
    dayNumber: Int,
    dayPitstopsOn: Boolean,
    onDayPitstopsChange: (Boolean) -> Unit,
    totalDays: Int,
    computedPitstops: List<RouteRepository.Pitstop>,
    findingPitstops: Boolean,
    enabled: Boolean,
    hasRoute: Boolean,
    onGeneratePitstops: () -> Unit,
    onAddToItinerary: () -> Unit,
    pitstopMessage: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Smart Pitstop Engine", style = MaterialTheme.typography.titleMedium)
        }

        ElevatedCard {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (totalDays > 1) "Suggest pitstops on Day $dayNumber" else "Suggest pitstops for this trip",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            if (totalDays > 1) {
                                "Each day has its own switch, so a sightseeing day can skip breaks while the long drives keep them."
                            } else {
                                "Turn off if your group doesn't want any breaks suggested along the route."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dayPitstopsOn,
                        onCheckedChange = onDayPitstopsChange
                    )
                }

                if (!dayPitstopsOn) {
                    // Without pitstops there was no way to get the route into the Itinerary at all.
                    OutlinedButton(
                        onClick = onAddToItinerary,
                        enabled = hasRoute && !findingPitstops,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Add Day $dayNumber route to Itinerary") }
                    pitstopMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    return@Column
                }

                HorizontalDivider()

                if (totalDays > 1) {
                    Text(
                        "Break settings below are shared by every day that has pitstops on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
                FlowRowPreferences(
                    selectedPreferences = plan.pitstopCategories,
                    onToggle = { pref ->
                        val updated = if (pref in plan.pitstopCategories) {
                            plan.pitstopCategories - pref
                        } else {
                            plan.pitstopCategories + pref
                        }
                        onPlanChange(plan.copy(pitstopCategories = updated))
                    }
                )

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
                        Text("Generate Day $dayNumber pitstops for Itinerary")
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
    dayNumber: Int,
    totalDays: Int,
    loading: Boolean,
    routeInfo: RouteRepository.RouteInfo?,
    hasRoute: Boolean,
    onOpenMaps: () -> Unit
) {
    ElevatedCard {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (totalDays > 1) "Day $dayNumber route summary" else "Route summary",
                    style = MaterialTheme.typography.titleMedium
                )
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
    groupName: String,
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
        var newPhone by remember { mutableStateOf("") }
        var newEmail by remember { mutableStateOf("") }
        val canAdd = newName.isNotBlank() && (newPhone.isNotBlank() || newEmail.isNotBlank())
        AlertDialog(
            onDismissRequest = { showAddPeople = false },
            title = { Text("Add a travel companion") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "A phone number or email is required so the group can reach them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPhone,
                        onValueChange = { newPhone = it },
                        label = { Text("Phone number") },
                        placeholder = { Text("Optional if email is given") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("Email") },
                        placeholder = { Text("Optional if phone is given") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (canAdd) {
                            val trimmedPhone = newPhone.trim()
                            val trimmedEmail = newEmail.trim()
                            scope.launchSafely(onError, "Couldn't add that person — check your internet connection.") {
                                // Best-effort account match: if this lookup fails for any reason
                                // (a rules hiccup, a transient error), adding the person below must
                                // still go through exactly as it always did -- account matching is
                                // a bonus on top of that, never a prerequisite for it. An earlier
                                // version of this code ran the lookup first without this guard, so
                                // any failure here silently blocked the add entirely.
                                val match = runCatching { TripRepository.findUserProfile(trimmedEmail, trimmedPhone) }.getOrNull()
                                val alreadyOnTrip = match?.let { m -> members.firstOrNull { it.uid == m.uid } }
                                if (alreadyOnTrip != null) {
                                    onError("${alreadyOnTrip.name} is already on this trip.")
                                    return@launchSafely
                                }
                                val memberId = TripRepository.joinTrip(
                                    session.tripCode,
                                    name,
                                    role = MemberRole.JOINER,
                                    phone = trimmedPhone,
                                    email = trimmedEmail,
                                    uid = match?.uid
                                )
                                if (match != null) {
                                    // Puts the trip on their homepage. Needs the updated
                                    // firestore.rules (creating an entry in someone else's list);
                                    // under the old rules this was silently refused.
                                    runCatching {
                                        TripRepository.recordTripAccess(match.uid, session.tripCode, groupName, memberId, MemberRole.JOINER)
                                    }
                                }
                            }
                        }
                        newName = ""
                        newPhone = ""
                        newEmail = ""
                        showAddPeople = false
                    },
                    enabled = canAdd
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPeople = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AutocompletePlaceField(
    label: String,
    value: Place,
    onValueChange: (Place) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowCurrentLocation: Boolean = false,
    /** Results close to this place come first (each To is searched near that day's From). */
    near: Place? = null
) {
    val context = LocalContext.current
    var results by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun useCurrentLocation() {
        busy = true
        expanded = false
        scope.launch {
            try {
                onValueChange(CurrentLocation.asPlace(context))
            } catch (e: CurrentLocation.Unavailable) {
                onMessage(e.message ?: "Couldn't get your location.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onMessage("Couldn't get your location. Please try again.")
            } finally {
                busy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            useCurrentLocation()
        } else {
            onMessage("Location access is off. Allow it in the phone's Settings to use your current location.")
        }
    }
    val askForLocation = {
        if (CurrentLocation.hasPermission(context)) useCurrentLocation() else permissionLauncher.launch(CurrentLocation.PERMISSIONS)
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value.name,
            onValueChange = { new ->
                // Typing replaces whatever place was picked before; a suggestion brings the exact
                // place back.
                onValueChange(Place(name = new))
                searchJob?.cancel()
                if (new.trim().length >= 2) {
                    searchJob = scope.launch {
                        delay(400) // debounce: wait for a pause in typing before searching
                        busy = true
                        val found = try {
                            PlaceSearch.search(new, near?.let { PlaceRules.usableLocation(it, System.currentTimeMillis()) })
                        } finally {
                            busy = false
                        }
                        results = found
                        expanded = found.isNotEmpty()
                    }
                } else {
                    results = emptyList()
                    expanded = false
                }
            },
            label = { Text(label) },
            placeholder = { Text("Search a place, address or landmark") },
            singleLine = true,
            supportingText = PlaceRules.describe(value, System.currentTimeMillis())?.let { line ->
                { Text(line, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            },
            trailingIcon = when {
                busy -> {
                    { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                }
                allowCurrentLocation -> {
                    {
                        IconButton(onClick = askForLocation) {
                            Icon(Icons.Filled.MyLocation, contentDescription = "Use my current location")
                        }
                    }
                }
                else -> null
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (allowCurrentLocation && value.name.isBlank() && !busy) {
            AssistChip(
                onClick = askForLocation,
                leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = { Text("Use my current location") }
            )
        }
        if (expanded && results.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column {
                    val shown = results.take(5)
                    shown.forEachIndexed { i, result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = false
                                    scope.launch {
                                        try {
                                            onValueChange(PlaceSearch.select(result))
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            onMessage("Couldn't load that place. Check your internet connection and try again.")
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                result.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (result.subtitle.isNotBlank()) {
                                Text(
                                    result.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (i < shown.lastIndex) HorizontalDivider()
                    }
                    // Both services require saying where results come from.
                    Text(
                        if (shown.firstOrNull()?.source == PlaceSource.GOOGLE) "Powered by Google" else "Results © OpenStreetMap contributors",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp, vertical = 6.dp)
                    )
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

private data class MapWaypoint(val text: String, val cumulativeKm: Double)

/** Opens Google Maps with turn-by-turn directions through the full route: the typed stops *and*
 *  any generated pitstops, merged into one waypoint list in true route order. Places with a saved
 *  location go to Maps as that exact point (or as Google's place, when picked from Google), so
 *  Maps opens the same spot that was picked here instead of searching the name again. Pitstops
 *  carry their distance from the start; named stops' distances come from the last-fetched
 *  [routeInfo]'s per-leg distances, so both sides are on the same scale. */
private fun openInGoogleMaps(
    context: Context,
    day: RouteDay,
    routeInfo: RouteRepository.RouteInfo?,
    pitstops: List<RouteRepository.Pitstop>
) {
    val now = System.currentTimeMillis()
    val stops = day.toStops.filter { it.name.isNotBlank() }
    if (day.from.name.isBlank() || stops.isEmpty()) return

    val chain = listOf(day.from) + stops
    val cumulativeKm = mutableListOf(0.0)
    if (routeInfo != null && routeInfo.legDistancesMeters.size >= chain.size - 1) {
        var running = 0.0
        for (i in 1 until chain.size) {
            running += routeInfo.legDistancesMeters[i - 1] / 1000.0
            cumulativeKm.add(running)
        }
    } else {
        // No route fetched yet (e.g. offline) — fall back to typed order, pitstops omitted since
        // we have no shared distance scale to place them on.
        for (i in 1 until chain.size) cumulativeKm.add(i.toDouble())
    }

    val lastNamed = MapWaypoint(PlaceRules.waypointText(chain.last(), now), cumulativeKm.last())
    val intermediateNamed = chain.drop(1).dropLast(1)
        .mapIndexed { i, place -> MapWaypoint(PlaceRules.waypointText(place, now), cumulativeKm[i + 1]) }
    val pitstopPoints = if (routeInfo != null) pitstops.map { MapWaypoint(it.placeName, it.distanceKm) } else emptyList()

    // On a round trip the last typed stop is a waypoint too, and pitstops found on the drive back
    // (further along than that stop) must come after it, so everything is sorted together.
    val waypointTexts = if (day.roundTrip) {
        (intermediateNamed + pitstopPoints + lastNamed).sortedBy { it.cumulativeKm }.map { it.text }
    } else {
        (intermediateNamed + pitstopPoints).sortedBy { it.cumulativeKm }.map { it.text }
    }

    val (originText, originPlaceId) = PlaceRules.mapsTarget(day.from, now)
    val (destinationText, destinationPlaceId) = PlaceRules.mapsTarget(if (day.roundTrip) day.from else chain.last(), now)
    val encode = { text: String -> URLEncoder.encode(text, "UTF-8") }

    val url = buildString {
        append("https://www.google.com/maps/dir/?api=1&origin=${encode(originText)}&destination=${encode(destinationText)}&travelmode=driving")
        if (originPlaceId != null) append("&origin_place_id=${encode(originPlaceId)}")
        if (destinationPlaceId != null) append("&destination_place_id=${encode(destinationPlaceId)}")
        if (waypointTexts.isNotEmpty()) append("&waypoints=${waypointTexts.joinToString("|") { encode(it) }}")
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Couldn't open Google Maps — is it installed?", Toast.LENGTH_LONG).show()
    }
}
