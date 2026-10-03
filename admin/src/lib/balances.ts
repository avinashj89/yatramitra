import type { Balance, Expense, Member, Settlement } from "../types";

// A line-for-line port of the app's Balances.kt, so the panel shows exactly the balances and
// settle-up payments that people see on their phones.

export function computeBalances(members: Member[], expenses: Expense[]): Balance[] {
  const net = new Map<string, number>(members.map((m) => [m.id, 0]));
  for (const expense of expenses) {
    net.set(expense.paidByMemberId, (net.get(expense.paidByMemberId) ?? 0) + expense.amount);
    const custom = Object.entries(expense.customSplitAmounts);
    if (custom.length > 0) {
      for (const [memberId, share] of custom) net.set(memberId, (net.get(memberId) ?? 0) - share);
    } else {
      const participants = expense.splitAmongMemberIds.length > 0 ? expense.splitAmongMemberIds : members.map((m) => m.id);
      if (participants.length === 0) continue;
      const share = expense.amount / participants.length;
      for (const memberId of participants) net.set(memberId, (net.get(memberId) ?? 0) - share);
    }
  }
  return members.map((m) => ({ memberId: m.id, name: m.name, net: net.get(m.id) ?? 0 }));
}

export function computeSettlements(balances: Balance[]): Settlement[] {
  const creditors = balances.filter((b) => b.net > 0.01).map((b) => ({ id: b.memberId, amount: b.net }));
  const debtors = balances.filter((b) => b.net < -0.01).map((b) => ({ id: b.memberId, amount: -b.net }));
  const names = new Map(balances.map((b) => [b.memberId, b.name]));
  const result: Settlement[] = [];
  let ci = 0;
  let di = 0;
  while (ci < creditors.length && di < debtors.length) {
    const amount = Math.min(creditors[ci].amount, debtors[di].amount);
    if (amount > 0.01) {
      result.push({ fromName: names.get(debtors[di].id) ?? "", toName: names.get(creditors[ci].id) ?? "", amount });
    }
    creditors[ci].amount -= amount;
    debtors[di].amount -= amount;
    if (Math.abs(creditors[ci].amount) < 0.01) ci++;
    if (Math.abs(debtors[di].amount) < 0.01) di++;
  }
  return result;
}
