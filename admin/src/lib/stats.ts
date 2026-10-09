import dayjs from "dayjs";
import type { ActivityEvent, ChatMessage, DeviceToken, Expense, Member, Trip, UserProfile } from "../types";
import { SOS_LABELS } from "./parse";

// Everything the dashboard computes from the live data. Pure, so it is unit-tested.

export interface Kpis {
  users: number;
  trips: number;
  ongoing: number;
  started: number;
  completed: number;
  members: number;
  contactOnlyMembers: number;
  expenseTotal: number;
  expenseCount: number;
  chatLast7Days: number;
  devices: number;
  usersWithNotifications: number;
}

const DAY = 24 * 60 * 60 * 1000;

export function computeKpis(
  input: { trips: Trip[]; users: UserProfile[]; members: Member[]; expenses: Expense[]; chat: ChatMessage[]; devices: DeviceToken[] },
  now: number
): Kpis {
  const { trips, users, members, expenses, chat, devices } = input;
  return {
    users: users.length,
    trips: trips.length,
    ongoing: trips.filter((t) => t.status === "ONGOING").length,
    started: trips.filter((t) => t.status === "ONGOING" && t.startedAt > 0).length,
    completed: trips.filter((t) => t.status === "COMPLETED").length,
    members: members.length,
    contactOnlyMembers: members.filter((m) => !m.uid).length,
    expenseTotal: expenses.reduce((sum, e) => sum + e.amount, 0),
    expenseCount: expenses.length,
    chatLast7Days: chat.filter((c) => c.createdAt >= now - 7 * DAY).length,
    devices: devices.length,
    usersWithNotifications: new Set(devices.map((d) => d.uid).filter(Boolean)).size,
  };
}

export interface DayPoint {
  day: string; // "3 Oct"
  start: number;
  value: number;
}

/** One point per calendar day for the last [days] days (oldest first), each the count of
 *  [times] (or the sum of [weights]) falling on that day. */
export function dailySeries(times: number[], days: number, now: number, weights?: number[]): DayPoint[] {
  const today = dayjs(now).startOf("day");
  const points: DayPoint[] = [];
  for (let i = days - 1; i >= 0; i--) {
    const start = today.subtract(i, "day");
    points.push({ day: start.format("D MMM"), start: start.valueOf(), value: 0 });
  }
  const first = points[0].start;
  times.forEach((t, i) => {
    if (t < first || t > now) return;
    const index = Math.floor((dayjs(t).startOf("day").valueOf() - first) / DAY + 0.5);
    if (index >= 0 && index < points.length) points[index].value += weights ? weights[i] : 1;
  });
  return points;
}

export function countBy<T>(items: T[], key: (item: T) => string | null | undefined): Map<string, number> {
  const out = new Map<string, number>();
  for (const item of items) {
    const k = key(item);
    if (k) out.set(k, (out.get(k) ?? 0) + 1);
  }
  return out;
}

/** Trip codes each account is a member of. */
export function tripsByAccount(members: Member[]): Map<string, string[]> {
  const out = new Map<string, string[]>();
  for (const m of members) {
    if (!m.uid) continue;
    const list = out.get(m.uid) ?? [];
    if (!list.includes(m.tripCode)) list.push(m.tripCode);
    out.set(m.uid, list);
  }
  return out;
}

/** The newest things that happened anywhere in the app, newest first. */
export function activityFeed(
  input: { trips: Trip[]; members: Member[]; expenses: Expense[]; chat: ChatMessage[] },
  limit: number
): ActivityEvent[] {
  const names = new Map(input.trips.map((t) => [t.code, t.groupName]));
  const name = (code: string) => names.get(code) ?? code;
  const events: ActivityEvent[] = [];
  for (const t of input.trips) {
    if (t.createdAt > 0) events.push({ id: `t-${t.code}`, kind: "trip-created", at: t.createdAt, tripCode: t.code, tripName: t.groupName, text: `Trip "${t.groupName}" was created` });
    if (t.startedAt > 0) events.push({ id: `s-${t.code}`, kind: "trip-started", at: t.startedAt, tripCode: t.code, tripName: t.groupName, text: `${t.startedBy || "The Organizer"} started "${t.groupName}"` });
  }
  for (const m of input.members) {
    if (m.joinedAt > 0) {
      events.push({
        id: `m-${m.tripCode}-${m.id}`,
        kind: "member-joined",
        at: m.joinedAt,
        tripCode: m.tripCode,
        tripName: name(m.tripCode),
        text: m.role === "ORGANIZER" ? `${m.name} set up the trip as Organizer` : `${m.name} joined${m.uid ? "" : " (added as a contact)"}`,
      });
    }
  }
  for (const e of input.expenses) {
    events.push({
      id: `e-${e.tripCode}-${e.id}`,
      kind: "expense",
      at: e.createdAt,
      tripCode: e.tripCode,
      tripName: name(e.tripCode),
      text: `${e.paidByName || "Someone"} added ${formatAmount(e.amount)}${e.description ? ` for ${e.description}` : ""}`,
    });
  }
  for (const c of input.chat) {
    const text = c.sos ? `SOS (${SOS_LABELS[c.sos.type]}) from ${c.authorName}` : `${c.authorName}: ${c.text}`;
    events.push({ id: `c-${c.tripCode}-${c.id}`, kind: "chat", at: c.createdAt, tripCode: c.tripCode, tripName: name(c.tripCode), text });
  }
  return events.filter((e) => e.at > 0).sort((a, b) => b.at - a.at).slice(0, limit);
}

function formatAmount(amount: number): string {
  return "₹" + (Math.round(amount * 100) / 100).toLocaleString("en-IN", { maximumFractionDigits: 2 });
}
