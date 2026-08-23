package com.avinash.yatramitra.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.RoutePlan
import com.avinash.yatramitra.model.RoutePreference
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPlannerScreen() {
    var plan by remember { mutableStateOf(RoutePlan()) }
    val context = LocalContext.current

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
            value = plan.from,
            onValueChange = { plan = plan.copy(from = it) },
            label = { Text("From") },
            placeholder = { Text("e.g. Bengaluru") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = plan.to,
            onValueChange = { plan = plan.copy(to = it) },
            label = { Text("To") },
            placeholder = { Text("e.g. Dharmasthala") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = plan.roundTrip,
                onCheckedChange = { plan = plan.copy(roundTrip = it) }
            )
            Spacer(Modifier.width(4.dp))
            Text("This is a round trip (I'll return to the start)")
        }

        HorizontalDivider()

        Text("Break every", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = plan.breakEvery,
                onValueChange = { new -> if (new.all { it.isDigit() }) plan = plan.copy(breakEvery = new) },
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
                    onSelect = { plan = plan.copy(breakUnit = BreakUnit.KM) }
                )
                BreakUnitOption(
                    label = "Hours",
                    selected = plan.breakUnit == BreakUnit.HOURS,
                    onSelect = { plan = plan.copy(breakUnit = BreakUnit.HOURS) }
                )
            }
        }
        Text(
            "YatraMitra can't auto-place stops on the map for free, but it'll remind you here — add your actual break stops on the Itinerary tab.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        HorizontalDivider()

        Text("Route preference", style = MaterialTheme.typography.titleMedium)
        RoutePrefOption(
            label = "Fastest route",
            selected = plan.routePreference == RoutePreference.FASTEST,
            onSelect = { plan = plan.copy(routePreference = RoutePreference.FASTEST) }
        )
        RoutePrefOption(
            label = "Alternate route",
            selected = plan.routePreference == RoutePreference.ALTERNATE,
            onSelect = { plan = plan.copy(routePreference = RoutePreference.ALTERNATE) }
        )
        if (plan.routePreference == RoutePreference.ALTERNATE) {
            Text(
                "Google Maps will open with directions — tap \"Alternate routes\" inside Maps to switch off the fastest one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { openInGoogleMaps(context, plan) },
            enabled = plan.from.isNotBlank() && plan.to.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.Map, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (plan.roundTrip) "Open route in Google Maps (there & back)" else "Open route in Google Maps",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(24.dp))
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
    val origin = URLEncoder.encode(plan.from, "UTF-8")
    val destination = URLEncoder.encode(plan.to, "UTF-8")
    val url = if (plan.roundTrip) {
        // Round trip: route out to the destination and back, via a single Maps link with a waypoint.
        "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$origin&waypoints=$destination&travelmode=driving"
    } else {
        "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&travelmode=driving"
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Couldn't open Google Maps — is it installed?", Toast.LENGTH_LONG).show()
    }
}
