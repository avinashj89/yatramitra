package com.avinash.yatramitra.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.CarRepair
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.AuthRepository
import com.avinash.yatramitra.data.CurrentLocation
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.SosRules
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.model.ChatMessage
import com.avinash.yatramitra.model.Hospital
import com.avinash.yatramitra.model.SosAlert
import com.avinash.yatramitra.model.SosType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Picture and colour for each kind of SOS; white icons on strong colours read in light and dark. */
private data class SosStyle(val icon: ImageVector, val color: Color)

private fun sosStyle(type: SosType): SosStyle = when (type) {
    SosType.ACCIDENT -> SosStyle(Icons.Filled.CarCrash, Color(0xFFC62828))
    SosType.MEDICAL -> SosStyle(Icons.Filled.MedicalServices, Color(0xFFAD1457))
    SosType.BREAKDOWN -> SosStyle(Icons.Filled.CarRepair, Color(0xFFE65100))
    SosType.POLICE -> SosStyle(Icons.Filled.LocalPolice, Color(0xFF1565C0))
    SosType.FUEL -> SosStyle(Icons.Filled.LocalGasStation, Color(0xFF2E7D32))
    SosType.ATM -> SosStyle(Icons.Filled.CurrencyRupee, Color(0xFF6A1B9A))
}

/** Diagonal hazard stripes over a solid background, so an SOS can't be mistaken for chat. */
private fun Modifier.hazardStripes(): Modifier = drawBehind {
    val step = 22.dp.toPx()
    val stroke = 7.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(Color.White.copy(alpha = 0.10f), start = Offset(x, size.height), end = Offset(x + size.height, 0f), strokeWidth = stroke)
        x += step
    }
}

/** The red SOS button in the Group chat header. */
@Composable
fun SosButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(Icons.Filled.Sos, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text("SOS", fontWeight = FontWeight.Bold)
    }
}

private sealed interface SosPhase {
    data object Choose : SosPhase
    data class Sending(val type: SosType) : SosPhase
    data class Sent(val alert: SosAlert) : SosPhase
}

/**
 * Choose what's happening; one tap sends it to the Group chat (with this phone's location when
 * location is allowed) and everyone on the trip gets a loud notification. Then offers the right
 * numbers and nearby searches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosSheet(session: LocalStore.Session, myName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var phase by remember { mutableStateOf<SosPhase>(SosPhase.Choose) }
    var waitingForPermission by remember { mutableStateOf<SosType?>(null) }
    val savedHospitals = rememberTripHospitals(session.tripCode)

    fun send(type: SosType) {
        phase = SosPhase.Sending(type)
        scope.launch {
            val place = if (CurrentLocation.hasPermission(context)) {
                try {
                    CurrentLocation.asPlace(context, timeoutMillis = 8_000)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            val alert = SosAlert(type, place?.lat, place?.lng, place?.name.orEmpty())
            TripRepository.postSos(session.tripCode, session.memberId, myName, AuthRepository.currentUserId, alert)
            phase = SosPhase.Sent(alert)
        }
    }

    // Sends whether or not location was allowed: help shouldn't wait on a permission.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        waitingForPermission?.let(::send)
        waitingForPermission = null
    }
    fun choose(type: SosType) {
        if (CurrentLocation.hasPermission(context)) {
            send(type)
        } else {
            waitingForPermission = type
            permissionLauncher.launch(CurrentLocation.PERMISSIONS)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (val current = phase) {
                SosPhase.Choose -> SosChooser(onChoose = ::choose)

                is SosPhase.Sending -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Sending your ${SosRules.info(current.type).label} SOS…", style = MaterialTheme.typography.titleMedium)
                    }
                }

                is SosPhase.Sent -> {
                    val alert = current.alert
                    val nearest = if (SosRules.showsHospitals(alert.type) && alert.lat != null && alert.lng != null && savedHospitals != null) {
                        SosRules.nearestHospitals(savedHospitals.values, alert.lat, alert.lng)
                    } else {
                        emptyList()
                    }
                    SosSentContent(alert = alert, nearestHospitals = nearest, onDone = onDismiss)
                }
            }
        }
    }
}

/** The six kinds of help, as big picture tiles, plus direct-dial numbers. */
@Composable
fun SosChooser(onChoose: (SosType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Send an SOS to your trip group", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Tap what's happening. Everyone on the trip gets it straight away, with your location if location is on.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SosType.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { type -> SosTile(type, onClick = { onChoose(type) }, modifier = Modifier.weight(1f)) }
            }
        }
        Text("In danger right now? Call directly:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        EmergencyNumbers()
    }
}

/** After sending: confirmation, the right numbers and searches, and nearby hospitals. */
@Composable
fun SosSentContent(alert: SosAlert, nearestHospitals: List<Pair<Hospital, Double>>, onDone: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("SOS sent to your trip group", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (alert.lat != null) "With your location: ${alert.locationName}"
                    else "Your location couldn't be read, so it wasn't shared. Tell them where you are in the chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "No signal? It goes out as soon as your phone reconnects. Calling still works on any network.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SosRules.actions(alert.type).forEach { action ->
            when (action) {
                is SosRules.Action.Dial -> Button(
                    onClick = { EmergencyActions.dial(context, action.number) },
                    colors = ButtonDefaults.buttonColors(containerColor = sosStyle(alert.type).color, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(action.label)
                }
                is SosRules.Action.SearchMaps -> OutlinedButton(
                    onClick = { EmergencyActions.searchMaps(context, action.query) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(action.label)
                }
            }
        }
        if (SosRules.showsHospitals(alert.type)) {
            Text("Nearest hospitals", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (nearestHospitals.isEmpty()) {
                OutlinedButton(onClick = { EmergencyActions.searchMaps(context, "hospital") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Find hospitals on Google Maps")
                }
            } else {
                nearestHospitals.forEach { (hospital, km) ->
                    HospitalRow(hospital, "About ${String.format(Locale.US, "%.1f", km)} km from you")
                }
            }
        }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.End)) { Text("Done") }
    }
}

@Composable
private fun SosTile(type: SosType, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = sosStyle(type)
    val info = SosRules.info(type)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = style.color.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.5.dp, style.color.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp)
        ) {
            Box(
                modifier = Modifier.size(60.dp).background(style.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(style.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Text(info.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            Text(
                info.help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2
            )
        }
    }
}

/** An SOS in the chat: full width, solid colour with hazard stripes, the picture, who and where. */
@Composable
fun SosBubble(message: ChatMessage, isMine: Boolean, authorPhone: String?) {
    val alert = message.sos ?: return
    val style = sosStyle(alert.type)
    val context = LocalContext.current
    val time = remember(message.createdAtMillis) {
        if (message.createdAtMillis > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.createdAtMillis)) else ""
    }
    val white = Color.White
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(style.color)
            .hazardStripes()
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(white, CircleShape), contentAlignment = Alignment.Center) {
                Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("SOS · ${SosRules.info(alert.type).label}", color = white, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (isMine) "You asked the group for help" else "${message.authorName.ifBlank { "Someone" }} needs help",
                    color = white,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Text(
            if (alert.lat != null) "Location: ${alert.locationName}" else "Location wasn't shared",
            color = white.copy(alpha = 0.95f),
            style = MaterialTheme.typography.bodyMedium
        )
        if (time.isNotBlank()) Text(time, color = white.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val outline = ButtonDefaults.outlinedButtonColors(contentColor = white)
            if (alert.lat != null && alert.lng != null) {
                OutlinedButton(
                    onClick = { EmergencyActions.directions(context, alert.lat, alert.lng) },
                    colors = outline,
                    border = BorderStroke(1.dp, white)
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open location")
                }
            }
            if (!isMine && !authorPhone.isNullOrBlank()) {
                OutlinedButton(onClick = { EmergencyActions.dial(context, authorPhone) }, colors = outline, border = BorderStroke(1.dp, white)) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Call ${message.authorName.substringBefore(' ').ifBlank { "them" }}")
                }
            }
        }
    }
}
