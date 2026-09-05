package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.Balance
import com.avinash.yatramitra.model.Expense
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.Settlement
import kotlin.math.abs
import kotlin.math.min

/** Splitwise-style math: net balance per person, then the fewest payments to settle up. */
object Balances {

    fun computeBalances(members: List<Member>, expenses: List<Expense>): List<Balance> {
        val net = members.associate { it.id to 0.0 }.toMutableMap()
        for (expense in expenses) {
            net[expense.paidByMemberId] = (net[expense.paidByMemberId] ?: 0.0) + expense.amount
            if (expense.customSplitAmounts.isNotEmpty()) {
                // Custom split: each member's exact share, as entered when the expense was added.
                expense.customSplitAmounts.forEach { (memberId, share) ->
                    net[memberId] = (net[memberId] ?: 0.0) - share
                }
            } else {
                // Equal split among the chosen participants (or everyone, if none were specified).
                val participants = expense.splitAmongMemberIds.ifEmpty { members.map { it.id } }
                if (participants.isEmpty()) continue
                val share = expense.amount / participants.size
                participants.forEach { memberId ->
                    net[memberId] = (net[memberId] ?: 0.0) - share
                }
            }
        }
        return members.map { Balance(it.id, it.name, net[it.id] ?: 0.0) }
    }

    /** Greedy "largest creditor paid by largest debtor" settle-up plan. */
    fun computeSettlements(balances: List<Balance>): List<Settlement> {
        val creditors = balances.filter { it.net > 0.01 }
            .map { it.memberId to it.net }.toMutableList()
        val debtors = balances.filter { it.net < -0.01 }
            .map { it.memberId to -it.net }.toMutableList()
        val names = balances.associate { it.memberId to it.name }
        val result = mutableListOf<Settlement>()

        var ci = 0
        var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val (creditorId, creditAmt) = creditors[ci]
            val (debtorId, debtAmt) = debtors[di]
            val amount = min(creditAmt, debtAmt)
            if (amount > 0.01) {
                result.add(
                    Settlement(
                        fromMemberId = debtorId,
                        fromName = names[debtorId] ?: "",
                        toMemberId = creditorId,
                        toName = names[creditorId] ?: "",
                        amount = amount
                    )
                )
            }
            creditors[ci] = creditorId to (creditAmt - amount)
            debtors[di] = debtorId to (debtAmt - amount)
            if (abs(creditors[ci].second) < 0.01) ci++
            if (abs(debtors[di].second) < 0.01) di++
        }
        return result
    }
}
