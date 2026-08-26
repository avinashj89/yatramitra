package com.avinash.yatramitra.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.Balances
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.model.Expense
import com.avinash.yatramitra.model.Member
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private const val MAX_MEMBERS = 50

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
                        val plannerTripName = LocalStore.loadRoutePlan(context).tripName
                        val newCode = TripRepository.createTrip(groupName = plannerTripName)
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
    var groupName by remember { mutableStateOf("") }
    var showAddExpense by remember { mutableStateOf(false) }
    var showAddPeople by remember { mutableStateOf(false) }
    var editingGroupName by remember { mutableStateOf(false) }
    var groupNameDraft by remember { mutableStateOf("") }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LaunchedEffect(session.tripCode) {
        launch { TripRepository.observeMembers(session.tripCode).collect { members = it } }
        launch { TripRepository.observeExpenses(session.tripCode).collect { expenses = it } }
        launch { TripRepository.observeTripMeta(session.tripCode).collect { groupName = it.groupName } }
    }

    val balances = remember(members, expenses) { Balances.computeBalances(members, expenses) }
    val settlements = remember(balances) { Balances.computeSettlements(balances) }
    val totalExpense = remember(expenses) { expenses.sumOf { it.amount } }

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
                            if (editingGroupName) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = groupNameDraft,
                                        onValueChange = { groupNameDraft = it },
                                        singleLine = true,
                                        label = { Text("Group name") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        scope.launch {
                                            TripRepository.updateGroupName(session.tripCode, groupNameDraft)
                                            editingGroupName = false
                                        }
                                    }) { Icon(Icons.Filled.Check, contentDescription = "Save group name") }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        groupName.ifBlank { "Our trip" },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = {
                                            groupNameDraft = groupName
                                            editingGroupName = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Edit group name",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = { showAddPeople = true },
                                    enabled = members.size < MAX_MEMBERS
                                ) {
                                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add people")
                                }
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
                        Text("Trip code: ${session.tripCode}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${members.size} traveler${if (members.size == 1) "" else "s"}: " +
                                members.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total trip expense", style = MaterialTheme.typography.titleMedium)
                        Text(
                            currency.format(totalExpense),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
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
                            val splitLabel = if (expense.customSplitAmounts.isNotEmpty()) {
                                "Paid by ${expense.paidByName} · custom split, ${expense.customSplitAmounts.size} ways"
                            } else {
                                "Paid by ${expense.paidByName} · split ${expense.splitAmongMemberIds.size} ways"
                            }
                            Text(
                                splitLabel,
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

    if (showAddPeople) {
        AddPeopleDialog(
            currentMemberCount = members.size,
            onAddName = { name ->
                scope.launch { TripRepository.joinTrip(session.tripCode, name) }
            },
            onDismiss = { showAddPeople = false }
        )
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
private fun AddPeopleDialog(
    currentMemberCount: Int,
    onAddName: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var addedCount by remember { mutableStateOf(0) }
    val remaining = MAX_MEMBERS - currentMemberCount - addedCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add people") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Add travel companions by name — they don't need to install the app themselves. " +
                        "Up to $MAX_MEMBERS people per trip.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (remaining > 0) "$remaining more can be added" else "Trip is at the $MAX_MEMBERS person limit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && remaining > 0) {
                        onAddName(name.trim())
                        addedCount++
                        name = ""
                    }
                },
                enabled = name.isNotBlank() && remaining > 0
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var splitEqually by remember { mutableStateOf(true) }
    val splitAmong = remember { mutableStateListOf(*members.map { it.id }.toTypedArray()) }
    val customAmounts = remember { mutableStateMapOf<String, String>() }

    val amountValue = amount.toDoubleOrNull() ?: 0.0

    // When switching into custom-split mode, seed each checked member with an equal starting share.
    LaunchedEffect(splitEqually) {
        if (!splitEqually) {
            val share = if (splitAmong.isNotEmpty()) amountValue / splitAmong.size else 0.0
            splitAmong.forEach { id ->
                if (customAmounts[id] == null) customAmounts[id] = "%.2f".format(share)
            }
        }
    }

    // Keep customAmounts in sync when checkboxes change while in custom mode.
    fun onMemberCheckedChange(memberId: String, checked: Boolean) {
        if (checked) {
            splitAmong.add(memberId)
            if (!splitEqually && customAmounts[memberId] == null) customAmounts[memberId] = "0.00"
        } else {
            splitAmong.remove(memberId)
            customAmounts.remove(memberId)
        }
    }

    val allocatedTotal = customAmounts.filterKeys { splitAmong.contains(it) }
        .values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val customValid = splitEqually || (
        kotlin.math.abs(allocatedTotal - amountValue) < 0.01 &&
            splitAmong.all { (customAmounts[it]?.toDoubleOrNull() ?: -1.0) >= 0.0 }
        )

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
                            onCheckedChange = { checked -> onMemberCheckedChange(m.id, checked) }
                        )
                        Text(m.name, modifier = Modifier.weight(1f))
                        if (!splitEqually && splitAmong.contains(m.id)) {
                            OutlinedTextField(
                                value = customAmounts[m.id] ?: "",
                                onValueChange = { new -> if (new.all { it.isDigit() || it == '.' }) customAmounts[m.id] = new },
                                label = { Text("₹") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Split equally", fontWeight = FontWeight.Medium)
                    Switch(checked = splitEqually, onCheckedChange = { splitEqually = it })
                }

                if (!splitEqually) {
                    val diff = amountValue - allocatedTotal
                    val message = when {
                        kotlin.math.abs(diff) < 0.01 -> "Allocated: ₹%.2f of ₹%.2f — matches exactly".format(allocatedTotal, amountValue)
                        diff > 0 -> "₹%.2f left to allocate".format(diff)
                        else -> "Over by ₹%.2f — reduce someone's share".format(-diff)
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (customValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val payerName = members.find { it.id == paidBy }?.name ?: currentMemberName
                    onSave(
                        Expense(
                            description = description.ifBlank { "Expense" },
                            amount = amountValue,
                            paidByMemberId = paidBy,
                            paidByName = payerName,
                            splitAmongMemberIds = splitAmong.toList(),
                            customSplitAmounts = if (splitEqually) emptyMap() else
                                splitAmong.associateWith { customAmounts[it]?.toDoubleOrNull() ?: 0.0 }
                        )
                    )
                },
                enabled = description.isNotBlank() && amountValue > 0 && splitAmong.isNotEmpty() && customValid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
