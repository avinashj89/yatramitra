import { describe, expect, it } from "vitest";
import { computeBalances, computeSettlements } from "../src/lib/balances";
import { millis, parseChat, parseExpense, parseMember, parseRoutePlan, parseTrip } from "../src/lib/parse";
import { activityFeed, computeKpis, dailySeries, tripsByAccount } from "../src/lib/stats";
import { mergeData } from "../src/data/DataContext";
import { emptyData } from "../src/data/source";
import { tripStage } from "../src/components/ui";
import type { Expense, Member } from "../src/types";

const member = (id: string, name: string, extra: Partial<Member> = {}): Member => ({
  id, tripCode: "T1", name, role: "JOINER", phone: "", email: "", uid: null, upiId: "", joinedAt: 0, ...extra,
});
const expense = (id: string, amount: number, paidBy: string, extra: Partial<Expense> = {}): Expense => ({
  id, tripCode: "T1", description: "", amount, paidByMemberId: paidBy, paidByName: "", splitAmongMemberIds: [], customSplitAmounts: {}, createdAt: 0, ...extra,
});

describe("balances (same maths as the app's Balances.kt)", () => {
  const a = member("a", "Asha");
  const b = member("b", "Bala");
  const c = member("c", "Chitra");

  it("splits equally among everyone when nobody is chosen", () => {
    const balances = computeBalances([a, b, c], [expense("1", 300, "a")]);
    expect(balances.map((x) => Math.round(x.net))).toEqual([200, -100, -100]);
  });

  it("uses the chosen people and custom amounts", () => {
    const balances = computeBalances(
      [a, b, c],
      [expense("1", 100, "a", { splitAmongMemberIds: ["a", "b"] }), expense("2", 90, "b", { customSplitAmounts: { a: 60, c: 30 } })]
    );
    expect(balances.map((x) => Math.round(x.net))).toEqual([-10, 40, -30]);
  });

  it("settles with the fewest payments and nobody left owing", () => {
    const balances = computeBalances([a, b, c], [expense("1", 300, "a")]);
    const settlements = computeSettlements(balances);
    expect(settlements).toEqual([
      { fromName: "Bala", toName: "Asha", amount: 100 },
      { fromName: "Chitra", toName: "Asha", amount: 100 },
    ]);
  });

  it("everyone even means nothing to settle", () => {
    expect(computeSettlements(computeBalances([a, b], []))).toEqual([]);
  });
});

describe("parsing Firestore documents", () => {
  it("reads a trip with a multi-day route", () => {
    const trip = parseTrip("ABC234", {
      groupName: "Coorg",
      status: "COMPLETED",
      createdAt: 5,
      startedAt: 7,
      routePlan: { days: [{ from: "A", toStops: ["B"], roundTrip: true }, { from: "B", toStops: [] }], breakEvery: "2", breakUnit: "KM" },
    });
    expect(trip.status).toBe("COMPLETED");
    expect(trip.routePlan?.days).toEqual([
      { from: "A", toStops: ["B"], roundTrip: true, pitstopsEnabled: true },
      { from: "B", toStops: [""], roundTrip: false, pitstopsEnabled: true },
    ]);
    expect(trip.routePlan?.breakUnit).toBe("KM");
  });

  it("reads an older single-route trip as Day 1, with safe defaults", () => {
    const trip = parseTrip("X", { routePlan: { from: "Pune", toStops: ["Goa"], roundTrip: false } });
    expect(trip.groupName).toBe("Our trip");
    expect(trip.status).toBe("ONGOING");
    expect(trip.routePlan?.days).toEqual([{ from: "Pune", toStops: ["Goa"], roundTrip: false, pitstopsEnabled: true }]);
    const oldOff = parseTrip("Y", { routePlan: { days: [{ from: "A" }, { from: "B", pitstopsEnabled: true }], pitstopsEnabled: false } });
    expect(oldOff.routePlan?.days.map((d) => d.pitstopsEnabled)).toEqual([false, true]);
    expect(parseRoutePlan(undefined)).toBeNull();
  });

  it("reads members, expenses and chat, ignoring junk values", () => {
    expect(parseMember("T", "m", { name: "Ravi", role: "ORGANIZER", uid: "" }).uid).toBeNull();
    expect(parseMember("T", "m", { role: "weird" }).role).toBe("JOINER");
    const e = parseExpense("T", "e", { amount: "12", customSplitAmounts: { a: 5, b: "x" }, splitAmongMemberIds: ["a", 3] });
    expect(e.amount).toBe(0);
    expect(e.customSplitAmounts).toEqual({ a: 5 });
    expect(e.splitAmongMemberIds).toEqual(["a"]);
    expect(parseChat("T", "c", { text: "hi" }).authorName).toBe("Someone");
    expect(parseChat("T", "c", { text: "hi" }).sos).toBeNull();
    expect(parseChat("T", "c", { kind: "sos", sosType: "ACCIDENT", lat: 12.5, lng: 77.5, locationName: "Near X" }).sos).toEqual({
      type: "ACCIDENT", lat: 12.5, lng: 77.5, locationName: "Near X",
    });
    expect(parseChat("T", "c", { kind: "sos", sosType: "ALIENS" }).sos).toBeNull();
    expect(parseChat("T", "c", { kind: "sos", sosType: "FUEL", lat: "x" }).sos?.lat).toBeNull();
  });

  it("accepts millisecond numbers and Firestore timestamps", () => {
    expect(millis(42)).toBe(42);
    expect(millis({ toMillis: () => 99 })).toBe(99);
    expect(millis("nope")).toBe(0);
  });
});

describe("dashboard numbers", () => {
  const now = new Date(2026, 9, 4, 12, 0).getTime();
  const DAY = 86_400_000;

  it("counts trips by stage and expenses", () => {
    const k = computeKpis(
      {
        trips: [parseTrip("A", { status: "ONGOING" }), parseTrip("B", { status: "ONGOING", startedAt: 1 }), parseTrip("C", { status: "COMPLETED" })],
        users: [{ uid: "u1", name: "", email: "", phone: "" }, { uid: "u2", name: "", email: "", phone: "" }],
        members: [member("1", "x", { uid: "u1" }), member("2", "y")],
        expenses: [expense("1", 100.5, "1"), expense("2", 50, "1")],
        chat: [parseChat("A", "c1", { createdAt: now - DAY }), parseChat("A", "c2", { createdAt: now - 10 * DAY })],
        devices: [{ token: "t1", uid: "u1", platform: "android", updatedAt: 0 }, { token: "t2", uid: "u1", platform: "android", updatedAt: 0 }],
      },
      now
    );
    expect(k).toMatchObject({ trips: 3, ongoing: 2, started: 1, completed: 1, members: 2, contactOnlyMembers: 1, expenseTotal: 150.5, chatLast7Days: 1, devices: 2, usersWithNotifications: 1 });
  });

  it("buckets events into calendar days, oldest first", () => {
    const series = dailySeries([now - 1000, now - DAY, now - DAY - 1000, now - 40 * DAY], 7, now);
    expect(series).toHaveLength(7);
    expect(series[6].value).toBe(1);
    expect(series[5].value).toBe(2);
    expect(series.reduce((s, p) => s + p.value, 0)).toBe(3);
    const sums = dailySeries([now - 1000, now - 2000], 3, now, [100, 50]);
    expect(sums[2].value).toBe(150);
  });

  it("lists each account's trips once", () => {
    const map = tripsByAccount([member("1", "x", { uid: "u1", tripCode: "A" }), member("2", "x", { uid: "u1", tripCode: "A" }), member("3", "x", { uid: "u1", tripCode: "B" })]);
    expect(map.get("u1")).toEqual(["A", "B"]);
  });

  it("builds the activity feed newest first with trip names", () => {
    const feed = activityFeed(
      {
        trips: [parseTrip("A", { groupName: "Goa", createdAt: 1, startedAt: 5, startedBy: "Avi" })],
        members: [member("1", "Ravi", { tripCode: "A", joinedAt: 2, uid: "u" })],
        expenses: [expense("e", 99, "1", { tripCode: "A", createdAt: 3, paidByName: "Ravi", description: "Tea" })],
        chat: [parseChat("A", "c", { authorName: "Sita", text: "hi", createdAt: 4 })],
      },
      10
    );
    expect(feed.map((e) => e.kind)).toEqual(["trip-started", "chat", "expense", "member-joined", "trip-created"]);
    expect(feed[2].text).toBe("Ravi added ₹99 for Tea");
    expect(feed.every((e) => e.tripName === "Goa")).toBe(true);
  });
});

describe("live data merging", () => {
  it("keeps other collections and clears an error once data arrives", () => {
    const withError = mergeData(emptyData, { errors: { chat: "boom" }, ready: { ...emptyData.ready, chat: true } });
    expect(withError.errors.chat).toBe("boom");
    const fixed = mergeData(withError, { chat: [], errors: { chat: undefined }, ready: { ...withError.ready } });
    expect(fixed.errors.chat).toBeUndefined();
    expect(fixed.ready.chat).toBe(true);
    expect(fixed.trips).toBe(emptyData.trips);
  });
});

describe("trip stage labels", () => {
  it("matches the app: planning, started, completed", () => {
    expect(tripStage({ status: "ONGOING", startedAt: 0 })).toBe("Planning");
    expect(tripStage({ status: "ONGOING", startedAt: 9 })).toBe("Started");
    expect(tripStage({ status: "COMPLETED", startedAt: 9 })).toBe("Completed");
  });
});
