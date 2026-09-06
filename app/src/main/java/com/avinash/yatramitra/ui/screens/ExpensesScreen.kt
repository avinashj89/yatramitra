package com.avinash.yatramitra.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.ui.util.launchSafely
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private const val MAX_MEMBERS = 50

@Composable
fun ExpensesScreen(session: LocalStore.Session, isReadOnly: Boolean, onError: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var groupName by remember { mutableStateOf("") }
    var showAddExpense by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var showAddPeople by remember { mutableStateOf(false) }
    var editingGroupName by remember { mutableStateOf(false) }
    var groupNameDraft by remember { mutableStateOf("") }
    var showUpiDialog by remember { mutableStateOf(false) }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }

    LaunchedEffect(session.tripCode) {
        launch { TripRepository.observeMembers(session.tripCode).collect { members = it } }
        launch { TripRepository.observeExpenses(session.tripCode).collect { expenses = it } }
        launch { TripRepository.observeTripMeta(session.tripCode).collect { groupName = it.groupName } }
    }

    val balances = remember(members, expenses) { Balances.computeBalances(members, expenses) }
    val settlements = remember(balances) { Balances.computeSettlements(balances) }
    val totalExpense = remember(expenses) { expenses.sumOf { it.amount } }
    val myBalance = balances.find { it.memberId == session.memberId }
    val myTotalPaid = remember(expenses, session.memberId) {
        expenses.filter { it.paidByMemberId == session.memberId }.sumOf { it.amount }
    }
    val myShare = myTotalPaid - (myBalance?.net ?: 0.0)
    val myUpiId = members.find { it.id == session.memberId }?.upiId.orEmpty()

    Scaffold(
        floatingActionButton = {
            if (!isReadOnly) {
                FloatingActionButton(onClick = {
                    editingExpense = null
                    showAddExpense = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add expense")
                }
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
                                        scope.launchSafely(onError, "Couldn't save the group name — check your internet connection.") {
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
                                    if (!isReadOnly) {
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
                            }
                            Row {
                                if (!isReadOnly) {
                                    IconButton(
                                        onClick = { showAddPeople = true },
                                        enabled = members.size < MAX_MEMBERS
                                    ) {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = "Add people")
                                    }
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
                            }
                        }
                        Text("Trip code: ${session.tripCode}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${members.size} traveler${if (members.size == 1) "" else "s"}: " +
                                members.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (isReadOnly) Modifier else Modifier.clickable { showUpiDialog = true }
                        ) {
                            Icon(
                                Icons.Filled.AccountBalance,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                when {
                                    isReadOnly && myUpiId.isBlank() -> "No UPI ID on file"
                                    isReadOnly -> "Your UPI ID: $myUpiId"
                                    myUpiId.isBlank() -> "Add your UPI ID for instant settle-up"
                                    else -> "Your UPI ID: $myUpiId (tap to edit)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Your share",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(currency.format(myShare), fontWeight = FontWeight.Bold)
                            }
                            val net = myBalance?.net ?: 0.0
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    when {
                                        net > 0.01 -> "You are owed"
                                        net < -0.01 -> "You owe"
                                        else -> "You're settled up"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (abs(net) > 0.01) {
                                    Text(
                                        currency.format(abs(net)),
                                        fontWeight = FontWeight.Bold,
                                        color = if (net > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
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
                                settlements.forEach { settlement ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${settlement.fromName} pays ${settlement.toName} ${currency.format(settlement.amount)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (settlement.fromMemberId == session.memberId) {
                                            val creditorUpiId = members.find { it.id == settlement.toMemberId }?.upiId.orEmpty()
                                            if (creditorUpiId.isNotBlank()) {
                                                TextButton(onClick = {
                                                    payViaUpi(context, creditorUpiId, settlement.toName, settlement.amount)
                                                }) { Text("Pay via UPI") }
                                            } else {
                                                Text(
                                                    "No UPI ID",
                                                    style = MaterialTheme.typography.labelSmall,
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(expense.description, fontWeight = FontWeight.Medium)
                            val splitLabel = when {
                                expense.splitPercentages.isNotEmpty() ->
                                    "Paid by ${expense.paidByName} · split by %, ${expense.splitPercentages.size} ways"
                                expense.customSplitAmounts.isNotEmpty() ->
                                    "Paid by ${expense.paidByName} · exact split, ${expense.customSplitAmounts.size} ways"
                                else ->
                                    "Paid by ${expense.paidByName} · split equally, ${expense.splitAmongMemberIds.size} ways"
                            }
                            Text(
                                splitLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(currency.format(expense.amount), fontWeight = FontWeight.Bold)
                        if (!isReadOnly) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    editingExpense = expense
                                    showAddExpense = true
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit expense", modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            IconButton(
                                onClick = {
                                    scope.launchSafely(onError, "Couldn't delete that expense — check your internet connection.") {
                                        TripRepository.deleteExpense(session.tripCode, expense.id)
                                    }
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete expense", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUpiDialog) {
        UpiIdDialog(
            initialUpiId = myUpiId,
            onDismiss = { showUpiDialog = false },
            onSave = { upiId ->
                scope.launchSafely(onError, "Couldn't save your UPI ID — check your internet connection.") {
                    TripRepository.updateMemberUpiId(session.tripCode, session.memberId, upiId)
                }
                showUpiDialog = false
            }
        )
    }

    if (showAddPeople) {
        AddPeopleDialog(
            currentMemberCount = members.size,
            onAdd = { name, phone, email ->
                scope.launchSafely(onError, "Couldn't add that person — check your internet connection.") {
                    TripRepository.joinTrip(session.tripCode, name, role = MemberRole.JOINER, phone = phone, email = email)
                }
            },
            onDismiss = { showAddPeople = false }
        )
    }

    if (showAddExpense) {
        AddExpenseDialog(
            members = members,
            currentMemberId = session.memberId,
            currentMemberName = session.memberName,
            initial = editingExpense,
            onDismiss = {
                showAddExpense = false
                editingExpense = null
            },
            onSave = { expense ->
                // Keep the dialog open on failure (rather than closing it) so the user doesn't
                // lose what they typed and can just retry.
                scope.launchSafely(onError, "Couldn't save that expense — check your internet connection and try again.") {
                    if (editingExpense == null) {
                        TripRepository.addExpense(session.tripCode, expense)
                    } else {
                        TripRepository.updateExpense(session.tripCode, expense)
                    }
                    showAddExpense = false
                    editingExpense = null
                }
            }
        )
    }
}

@Composable
private fun AddPeopleDialog(
    currentMemberCount: Int,
    onAdd: (name: String, phone: String, email: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var addedCount by remember { mutableStateOf(0) }
    val remaining = MAX_MEMBERS - currentMemberCount - addedCount
    val canAdd = name.isNotBlank() && (phone.isNotBlank() || email.isNotBlank()) && remaining > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add people") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Add travel companions by name — they don't need to install the app themselves. " +
                        "A phone number or email is required so the group can reach them. Up to $MAX_MEMBERS people per trip.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone number") },
                    placeholder = { Text("Optional if email is given") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    placeholder = { Text("Optional if phone is given") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
                    if (canAdd) {
                        onAdd(name.trim(), phone.trim(), email.trim())
                        addedCount++
                        name = ""
                        phone = ""
                        email = ""
                    }
                },
                enabled = canAdd
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun UpiIdDialog(
    initialUpiId: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var upiId by remember { mutableStateOf(initialUpiId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your UPI ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Optional — lets other travelers pay you directly via UPI when settling up.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text("UPI ID") },
                    placeholder = { Text("yourname@bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(upiId.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Opens a UPI app pre-filled to pay the given amount to the given VPA — same "try, fall back to
 *  a toast" pattern as the existing Google Maps deep link elsewhere in the app. Not a real payment
 *  integration; it just hands off to whichever UPI app (GPay/PhonePe/Paytm/...) the user has. */
private fun payViaUpi(context: Context, vpa: String, payeeName: String, amount: Double) {
    val uri = Uri.parse("upi://pay").buildUpon()
        .appendQueryParameter("pa", vpa)
        .appendQueryParameter("pn", payeeName)
        .appendQueryParameter("am", "%.2f".format(amount))
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", "YatraMitra settle-up")
        .build()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No UPI app found to complete the payment.", Toast.LENGTH_LONG).show()
    }
}

/** Loosely validates a currency text field as it's typed: digits with at most one decimal
 *  point, so something like "12..5" is rejected outright instead of silently parsing to 0
 *  and leaving the user unsure why Save won't enable. */
private fun isValidAmountInput(text: String): Boolean = text.matches(Regex("^\\d*\\.?\\d*$"))

private enum class SplitMode { EQUAL, PERCENTAGE, EXACT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    members: List<Member>,
    currentMemberId: String,
    currentMemberName: String,
    initial: Expense? = null,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.let { "%.2f".format(it) } ?: "") }
    var paidBy by remember {
        val initialPaidBy = initial?.paidByMemberId
        mutableStateOf(
            if (initialPaidBy != null && members.any { it.id == initialPaidBy }) initialPaidBy else currentMemberId
        )
    }
    var paidByExpanded by remember { mutableStateOf(false) }
    var splitMode by remember {
        mutableStateOf(
            when {
                initial == null -> SplitMode.EQUAL
                initial.splitPercentages.isNotEmpty() -> SplitMode.PERCENTAGE
                initial.customSplitAmounts.isNotEmpty() -> SplitMode.EXACT
                else -> SplitMode.EQUAL
            }
        )
    }
    val splitAmong = remember {
        val initialSplit = initial?.splitAmongMemberIds?.ifEmpty { members.map { it.id } } ?: members.map { it.id }
        mutableStateListOf(*initialSplit.toTypedArray())
    }
    val customAmounts = remember {
        mutableStateMapOf<String, String>().apply {
            initial?.customSplitAmounts?.forEach { (id, share) -> put(id, "%.2f".format(share)) }
        }
    }
    val customPercentages = remember {
        mutableStateMapOf<String, String>().apply {
            initial?.splitPercentages?.forEach { (id, pct) -> put(id, "%.2f".format(pct)) }
        }
    }

    val amountValue = amount.toDoubleOrNull() ?: 0.0

    // When switching into % or exact mode, seed each checked member with an equal starting share.
    LaunchedEffect(splitMode) {
        when (splitMode) {
            SplitMode.EXACT -> {
                val share = if (splitAmong.isNotEmpty()) amountValue / splitAmong.size else 0.0
                splitAmong.forEach { id -> if (customAmounts[id] == null) customAmounts[id] = "%.2f".format(share) }
            }
            SplitMode.PERCENTAGE -> {
                val pct = if (splitAmong.isNotEmpty()) 100.0 / splitAmong.size else 0.0
                splitAmong.forEach { id -> if (customPercentages[id] == null) customPercentages[id] = "%.2f".format(pct) }
            }
            SplitMode.EQUAL -> Unit
        }
    }

    // Keep customAmounts/customPercentages in sync when checkboxes change.
    fun onMemberCheckedChange(memberId: String, checked: Boolean) {
        if (checked) {
            splitAmong.add(memberId)
            if (splitMode == SplitMode.EXACT && customAmounts[memberId] == null) customAmounts[memberId] = "0.00"
            if (splitMode == SplitMode.PERCENTAGE && customPercentages[memberId] == null) customPercentages[memberId] = "0.00"
        } else {
            splitAmong.remove(memberId)
            customAmounts.remove(memberId)
            customPercentages.remove(memberId)
        }
    }

    val allocatedAmountTotal = customAmounts.filterKeys { splitAmong.contains(it) }
        .values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val allocatedPercentTotal = customPercentages.filterKeys { splitAmong.contains(it) }
        .values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val splitValid = when (splitMode) {
        SplitMode.EQUAL -> true
        SplitMode.EXACT -> abs(allocatedAmountTotal - amountValue) < 0.01 &&
            splitAmong.all { (customAmounts[it]?.toDoubleOrNull() ?: -1.0) >= 0.0 }
        SplitMode.PERCENTAGE -> abs(allocatedPercentTotal - 100.0) < 0.5 &&
            splitAmong.all { (customPercentages[it]?.toDoubleOrNull() ?: -1.0) >= 0.0 }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add expense" else "Edit expense") },
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
                    onValueChange = { new -> if (isValidAmountInput(new)) amount = new },
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

                Text("Split type", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        SplitMode.EQUAL to "Equally",
                        SplitMode.PERCENTAGE to "By %",
                        SplitMode.EXACT to "Exact"
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = splitMode == mode,
                            onClick = { splitMode = mode },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
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
                        when {
                            splitMode == SplitMode.EXACT && splitAmong.contains(m.id) -> OutlinedTextField(
                                value = customAmounts[m.id] ?: "",
                                onValueChange = { new -> if (isValidAmountInput(new)) customAmounts[m.id] = new },
                                label = { Text("₹") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(100.dp)
                            )
                            splitMode == SplitMode.PERCENTAGE && splitAmong.contains(m.id) -> OutlinedTextField(
                                value = customPercentages[m.id] ?: "",
                                onValueChange = { new -> if (isValidAmountInput(new)) customPercentages[m.id] = new },
                                label = { Text("%") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }

                if (splitMode == SplitMode.EXACT) {
                    val diff = amountValue - allocatedAmountTotal
                    val message = when {
                        abs(diff) < 0.01 -> "Allocated: ₹%.2f of ₹%.2f — matches exactly".format(allocatedAmountTotal, amountValue)
                        diff > 0 -> "₹%.2f left to allocate".format(diff)
                        else -> "Over by ₹%.2f — reduce someone's share".format(-diff)
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (splitValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                } else if (splitMode == SplitMode.PERCENTAGE) {
                    val diff = 100.0 - allocatedPercentTotal
                    val message = when {
                        abs(diff) < 0.5 -> "Allocated: %.1f%% of 100%% — matches".format(allocatedPercentTotal)
                        diff > 0 -> "%.1f%% left to allocate".format(diff)
                        else -> "Over by %.1f%% — reduce someone's share".format(-diff)
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (splitValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
                            id = initial?.id ?: "",
                            description = description.ifBlank { "Expense" },
                            amount = amountValue,
                            paidByMemberId = paidBy,
                            paidByName = payerName,
                            splitAmongMemberIds = splitAmong.toList(),
                            customSplitAmounts = when (splitMode) {
                                SplitMode.EQUAL -> emptyMap()
                                SplitMode.EXACT -> splitAmong.associateWith { customAmounts[it]?.toDoubleOrNull() ?: 0.0 }
                                SplitMode.PERCENTAGE -> splitAmong.associateWith { id ->
                                    (customPercentages[id]?.toDoubleOrNull() ?: 0.0) / 100.0 * amountValue
                                }
                            },
                            splitPercentages = if (splitMode == SplitMode.PERCENTAGE) {
                                splitAmong.associateWith { customPercentages[it]?.toDoubleOrNull() ?: 0.0 }
                            } else {
                                emptyMap()
                            },
                            createdAtMillis = initial?.createdAtMillis ?: 0L
                        )
                    )
                },
                enabled = description.isNotBlank() && amountValue > 0 && splitAmong.isNotEmpty() && splitValid
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
