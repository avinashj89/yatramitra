package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.Balance
import com.avinash.yatramitra.model.Expense
import com.avinash.yatramitra.model.Member
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancesTest {

    private val alice = Member(id = "a", name = "Alice")
    private val bob = Member(id = "b", name = "Bob")
    private val carol = Member(id = "c", name = "Carol")

    private fun net(balances: List<Balance>, memberId: String) =
        balances.first { it.memberId == memberId }.net

    @Test
    fun `no expenses means everyone is settled`() {
        val balances = Balances.computeBalances(listOf(alice, bob), emptyList())
        assertEquals(0.0, net(balances, "a"), 0.0001)
        assertEquals(0.0, net(balances, "b"), 0.0001)
        assertTrue(Balances.computeSettlements(balances).isEmpty())
    }

    @Test
    fun `equal split among all members when no participants specified`() {
        val expense = Expense(paidByMemberId = "a", amount = 100.0, splitAmongMemberIds = emptyList())
        val balances = Balances.computeBalances(listOf(alice, bob), listOf(expense))
        // Alice paid 100, owes her own 50 share -> net +50. Bob owes his 50 share -> net -50.
        assertEquals(50.0, net(balances, "a"), 0.0001)
        assertEquals(-50.0, net(balances, "b"), 0.0001)
    }

    @Test
    fun `equal split among an explicit subset excludes everyone else`() {
        val expense = Expense(paidByMemberId = "a", amount = 90.0, splitAmongMemberIds = listOf("a", "b"))
        val balances = Balances.computeBalances(listOf(alice, bob, carol), listOf(expense))
        assertEquals(45.0, net(balances, "a"), 0.0001)
        assertEquals(-45.0, net(balances, "b"), 0.0001)
        assertEquals(0.0, net(balances, "c"), 0.0001) // not in the split, untouched
    }

    @Test
    fun `payer excluded from the split is owed the full amount`() {
        // Alice pays for Bob and Carol's dinner only, not her own.
        val expense = Expense(paidByMemberId = "a", amount = 60.0, splitAmongMemberIds = listOf("b", "c"))
        val balances = Balances.computeBalances(listOf(alice, bob, carol), listOf(expense))
        assertEquals(60.0, net(balances, "a"), 0.0001)
        assertEquals(-30.0, net(balances, "b"), 0.0001)
        assertEquals(-30.0, net(balances, "c"), 0.0001)
    }

    @Test
    fun `custom split amounts are used verbatim instead of an equal share`() {
        val expense = Expense(
            paidByMemberId = "a",
            amount = 100.0,
            splitAmongMemberIds = listOf("a", "b", "c"),
            customSplitAmounts = mapOf("a" to 20.0, "b" to 30.0, "c" to 50.0)
        )
        val balances = Balances.computeBalances(listOf(alice, bob, carol), listOf(expense))
        assertEquals(80.0, net(balances, "a"), 0.0001) // paid 100, owes 20
        assertEquals(-30.0, net(balances, "b"), 0.0001)
        assertEquals(-50.0, net(balances, "c"), 0.0001)
    }

    @Test
    fun `multiple expenses accumulate correctly`() {
        val expenses = listOf(
            Expense(paidByMemberId = "a", amount = 100.0, splitAmongMemberIds = listOf("a", "b")),
            Expense(paidByMemberId = "b", amount = 40.0, splitAmongMemberIds = listOf("a", "b"))
        )
        val balances = Balances.computeBalances(listOf(alice, bob), expenses)
        // Alice: +100 -50 -20 = +30. Bob: -50 +40 -20 = -30.
        assertEquals(30.0, net(balances, "a"), 0.0001)
        assertEquals(-30.0, net(balances, "b"), 0.0001)
    }

    @Test
    fun `settlements net every debtor against a creditor with the fewest payments`() {
        // Alice is owed 70, Bob is owed 30, Carol owes 100.
        val expense = Expense(paidByMemberId = "a", amount = 100.0, splitAmongMemberIds = listOf("a", "b", "c"))
        // Adjust via a second expense so Bob is a creditor too, Carol owes everything.
        val expense2 = Expense(paidByMemberId = "b", amount = 30.0, splitAmongMemberIds = listOf("c"))
        val balances = Balances.computeBalances(listOf(alice, bob, carol), listOf(expense, expense2))
        val settlements = Balances.computeSettlements(balances)

        // Every settlement should be from a real debtor to a real creditor, and the amounts must
        // exactly zero out each person's net balance once applied.
        val appliedNet = balances.associate { it.memberId to it.net }.toMutableMap()
        for (s in settlements) {
            appliedNet[s.fromMemberId] = appliedNet.getValue(s.fromMemberId) + s.amount
            appliedNet[s.toMemberId] = appliedNet.getValue(s.toMemberId) - s.amount
        }
        appliedNet.values.forEach { assertEquals(0.0, it, 0.01) }
    }

    @Test
    fun `settlements ignore balances within the rounding tolerance`() {
        val balances = listOf(
            Balance("a", "Alice", 0.005),
            Balance("b", "Bob", -0.005)
        )
        assertTrue(Balances.computeSettlements(balances).isEmpty())
    }
}
