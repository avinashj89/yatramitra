package com.avinash.yatramitra.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.avinash.yatramitra.data.Balances
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.model.Expense
import com.avinash.yatramitra.model.Member
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ExpensesRoute() {
    val context = LocalContext.current
    var session by remember { mutableStateOf<LocalStore.Session?>(null) }
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        session = LocalStore.loadSession(context)
        checked = true
    }

    if (!checked) return

    val current = session
    if (current == null) {
        JoinTripScreen(onJoined = { code, memberId, name ->
            LocalStore.saveSession(context, code, memberId, name)
            session = LocalStore.Session(code, memberId, name)
        })
    } else {
        ExpensesScreen(
            session = current,
            onLeaveTrip = {
                LocalStore.clearSession(context)
                session = null
            }
        )
    }
}

@Composable
private fun JoinTripScreen(onJoined: (String, String, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Trip expenses", style = MaterialTheme.typography.titleLarge)
        Text(
            "Split costs with your travel group, live. Create a new trip to get a join code, or enter one you already have.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                error = null
                loading = true
                scope.launch {
                    try {
                        val newCode = TripRepository.createTrip()
                        val memberId = TripRepository.joinTrip(newCode, name.ifBlank { "Traveler" })
                        onJoined(newCode, memberId, name.ifBlank { "Traveler" })
                    } catch (e: Exception) {
                        error = "Couldn't create a trip. Check your internet connection and try again."
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Create a new trip") }

        HorizontalDivider()

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase() },
            label = { Text("Or enter a trip code") },
            placeholder = { Text("e.g. 7F3K9Q") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = {
                error = null
                loading = true
                scope.launch {
                    try {
                        val exists = TripRepository.tripExists(code.trim())
                        if (!exists) {
                            error = "No trip found with that code."
                        } else {
                            val memberId = TripRepository.joinTrip(code.trim(), name.ifBlank { "Traveler" })
                            onJoined(code.trim().uppercase(), memberId, name.ifBlank { "Traveler" })
                        }
                    } catch (e: Exception) {
                        error = "Couldn't join — check your internet connection and try again."
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Join trip") }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ExpensesScreen(session: LocalStore.Session, onLeaveTrip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var showAddExpense by remember { mutableStateOf(false) }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LaunchedEffect(session.tripCode) {
        launch { TripRepository.observeMembers(session.tripCode).collect { members = it } }
        launch { TripRepository.observeExpenses(session.tripCode).collect { expenses = it } }
    }

    val balances = remember(members, expenses) { Balances.computeBalances(members, expenses) }
    val settlements = remember(balances) { Balances.computeSettlements(balances) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddExpense = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add expense")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Trip code", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    session.tripCode,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row {
                                IconButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Join our trip on YatraMitra! Enter this code in the Expenses tab: ${session.tripCode}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(send, "Share trip code"))
                                }) { Icon(Icons.Filled.Share, contentDescription = "Share code") }
                                IconButton(onClick = onLeaveTrip) {
                                    Icon(Icons.Filled.ExitToApp, contentDescription = "Leave trip")
                                }
                            }
                        }
                        Text(
                            "${members.size} traveler${if (members.size == 1) "" else "s"}: " +
                                members.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (balances.isNotEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Balances", style = MaterialTheme.typography.titleMedium)
                            balances.forEach { b ->
                                val label = when {
                                    b.net > 0.01 -> "${b.name} is owed ${currency.format(b.net)}"
                                    b.net < -0.01 -> "${b.name} owes ${currency.format(-b.net)}"
                                    else -> "${b.name} is settled up"
                                }
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (settlements.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Text("Suggested settle-up", style = MaterialTheme.typography.titleMedium)
                                settlements.forEach {
                                    Text(
                                        "${it.fromName} pays ${it.toName} ${currency.format(it.amount)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Text("Expenses", style = MaterialTheme.typography.titleMedium) }

            if (expenses.isEmpty()) {
                item {
                    Text(
                        "No expenses yet — tap + to add the first one.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            items(expenses, key = { it.id }) { expense ->
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(expense.description, fontWeight = FontWeight.Medium)
                            Text(
                                "Paid by ${expense.paidByName} · split ${expense.splitAmongMemberIds.size} ways",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(currency.format(expense.amount), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAddExpense) {
        AddExpenseDialog(
            members = members,
            currentMemberId = session.memberId,
            currentMemberName = session.memberName,
            onDismiss = { showAddExpense = false },
            onSave = { expense ->
                scope.launch {
                    TripRepository.addExpense(session.tripCode, expense)
                    showAddExpense = false
                }
            }
        )
    }
}

@Composable
private fun AddExpenseDialog(
    members: List<Member>,
    currentMemberId: String,
    currentMemberName: String,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf(currentMemberId) }
    var paidByExpanded by remember { mutableStateOf(false) }
    val splitAmong = remember { mutableStateListOf(*members.map { it.id }.toTypedArray()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add expense") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What was it for?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { new -> if (new.all { it.isDigit() || it == '.' }) amount = new },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(expanded = paidByExpanded, onExpandedChange = { paidByExpanded = it }) {
                    OutlinedTextField(
                        value = members.find { it.id == paidBy }?.name ?: currentMemberName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Paid by") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = paidByExpanded, onDismissRequest = { paidByExpanded = false }) {
                        members.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name) }, onClick = {
                                paidBy = m.id
                                paidByExpanded = false
                            })
                        }
                    }
                }

                Text("Split among", fontWeight = FontWeight.Medium)
                members.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = splitAmong.contains(m.id),
                            onCheckedChange = { checked ->
                                if (checked) splitAmong.add(m.id) else splitAmong.remove(m.id)
                            }
                        )
                        Text(m.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val payerName = members.find { it.id == paidBy }?.name ?: currentMemberName
                    onSave(
                        Expense(
                            description = description.ifBlank { "Expense" },
                            amount = amt,
                            paidByMemberId = paidBy,
                            paidByName = payerName,
                            splitAmongMemberIds = splitAmong.toList()
                        )
                    )
                },
                enabled = description.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0 && splitAmong.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
