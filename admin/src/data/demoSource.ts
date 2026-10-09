import type { AdminEntry, ChatMessage, DeviceToken, Expense, ItineraryDay, Member, Trip, TripSummary, UserProfile } from "../types";
import type { AdminData, DataSource, TripDetail } from "./source";

// Made-up sample data for trying the panel without signing in (open the panel with ?demo).
// Every screen shows a "Demo data" banner while this is in use; nothing here touches Firestore.

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

function build(now: number) {
  const users: UserProfile[] = [
    { uid: "u-asha", name: "Asha Rao", email: "asha@example.com", phone: "" },
    { uid: "u-vikram", name: "Vikram Shetty", email: "", phone: "+910000000001" },
    { uid: "u-meera", name: "Meera Iyer", email: "meera@example.com", phone: "" },
    { uid: "u-rahul", name: "Rahul Das", email: "rahul@example.com", phone: "+910000000002" },
    { uid: "u-neha", name: "Neha Kulkarni", email: "neha@example.com", phone: "" },
    { uid: "u-arjun", name: "Arjun Menon", email: "", phone: "+910000000003" },
  ];
  const trips: Trip[] = [
    {
      code: "CRG7K2", groupName: "Coorg monsoon weekend", status: "ONGOING", createdAt: now - 12 * DAY, startedAt: now - 2 * DAY, startedBy: "Asha Rao",
      routePlan: {
        days: [
          { from: "Bengaluru", toStops: ["Mysuru", "Madikeri"], roundTrip: false, pitstopsEnabled: true },
          { from: "Madikeri", toStops: ["Abbey Falls", "Raja's Seat"], roundTrip: true, pitstopsEnabled: false },
          { from: "Madikeri", toStops: ["Bengaluru"], roundTrip: false, pitstopsEnabled: true },
        ],
        breakEvery: "2", breakUnit: "HOURS", pitstopCategories: ["Dhabas & Highway Food"],
      },
    },
    {
      code: "GOA4X9", groupName: "Goa with college gang", status: "ONGOING", createdAt: now - 6 * DAY, startedAt: 0, startedBy: "",
      routePlan: { days: [{ from: "Pune", toStops: ["Panaji"], roundTrip: false, pitstopsEnabled: true }], breakEvery: "100", breakUnit: "KM", pitstopCategories: [] },
    },
    { code: "HMP3Q8", groupName: "Hampi heritage trail", status: "COMPLETED", createdAt: now - 28 * DAY, startedAt: now - 24 * DAY, startedBy: "Meera Iyer", routePlan: { days: [{ from: "Bengaluru", toStops: ["Hampi"], roundTrip: true, pitstopsEnabled: false }], breakEvery: "", breakUnit: "HOURS", pitstopCategories: [] } },
    { code: "OTY8M5", groupName: "Ooty family drive", status: "ONGOING", createdAt: now - 3 * DAY, startedAt: 0, startedBy: "", routePlan: null },
    { code: "WYD2T6", groupName: "Wayanad office offsite", status: "ONGOING", createdAt: now - 1 * DAY, startedAt: 0, startedBy: "", routePlan: null },
  ];

  const members: Member[] = [];
  const add = (tripCode: string, id: string, name: string, role: Member["role"], uid: string | null, joinedAgo: number, phone = "") =>
    members.push({ id, tripCode, name, role, uid, phone, email: "", upiId: role === "ORGANIZER" ? `${name.split(" ")[0].toLowerCase()}@okbank` : "", joinedAt: now - joinedAgo });
  add("CRG7K2", "m1", "Asha Rao", "ORGANIZER", "u-asha", 12 * DAY);
  add("CRG7K2", "m2", "Vikram Shetty", "JOINER", "u-vikram", 11 * DAY);
  add("CRG7K2", "m3", "Meera Iyer", "JOINER", "u-meera", 10 * DAY);
  add("CRG7K2", "m4", "Kiran (driver)", "JOINER", null, 9 * DAY, "+910000000099");
  add("GOA4X9", "m5", "Rahul Das", "ORGANIZER", "u-rahul", 6 * DAY);
  add("GOA4X9", "m6", "Neha Kulkarni", "JOINER", "u-neha", 5 * DAY);
  add("GOA4X9", "m7", "Arjun Menon", "JOINER", "u-arjun", 4 * DAY);
  add("HMP3Q8", "m8", "Meera Iyer", "ORGANIZER", "u-meera", 28 * DAY);
  add("HMP3Q8", "m9", "Asha Rao", "JOINER", "u-asha", 27 * DAY);
  add("OTY8M5", "m10", "Vikram Shetty", "ORGANIZER", "u-vikram", 3 * DAY);
  add("OTY8M5", "m11", "Amma", "JOINER", null, 3 * DAY, "+910000000098");
  add("WYD2T6", "m12", "Neha Kulkarni", "ORGANIZER", "u-neha", 1 * DAY);

  const expenses: Expense[] = [];
  const spend = (tripCode: string, id: string, description: string, amount: number, paidByMemberId: string, ago: number, split: string[] = []) => {
    const payer = members.find((m) => m.id === paidByMemberId);
    expenses.push({ id, tripCode, description, amount, paidByMemberId, paidByName: payer?.name ?? "", splitAmongMemberIds: split, customSplitAmounts: {}, createdAt: now - ago });
  };
  spend("CRG7K2", "e1", "Fuel", 3200, "m1", 2 * DAY);
  spend("CRG7K2", "e2", "Homestay (2 nights)", 7800, "m2", 2 * DAY - 3 * HOUR);
  spend("CRG7K2", "e3", "Breakfast at Mysuru", 640, "m3", 2 * DAY - 5 * HOUR, ["m1", "m2", "m3"]);
  spend("CRG7K2", "e4", "Coffee estate tour", 1500, "m1", 1 * DAY);
  spend("CRG7K2", "e5", "Dinner", 2150, "m2", 5 * HOUR);
  spend("GOA4X9", "e6", "Train tickets", 4200, "m5", 4 * DAY);
  spend("GOA4X9", "e7", "Scooter rental", 1800, "m6", 20 * HOUR);
  spend("HMP3Q8", "e8", "Guide", 1200, "m8", 25 * DAY);
  spend("HMP3Q8", "e9", "Lunch", 560, "m9", 25 * DAY - 4 * HOUR);
  spend("OTY8M5", "e10", "Toll", 340, "m10", 2 * HOUR);

  const chat: ChatMessage[] = [];
  const say = (tripCode: string, id: string, authorMemberId: string, text: string, ago: number) => {
    const author = members.find((m) => m.id === authorMemberId);
    chat.push({ id, tripCode, authorMemberId, authorName: author?.name ?? "Someone", text, createdAt: now - ago, source: "chat" });
  };
  say("CRG7K2", "c1", "m1", "Leaving at 5:30 sharp tomorrow, please be ready!", 3 * DAY);
  say("CRG7K2", "c2", "m2", "Can we stop for breakfast in Mysuru?", 3 * DAY - 2 * HOUR);
  say("CRG7K2", "c3", "m1", "Yes, added it to Day 1.", 3 * DAY - HOUR);
  say("CRG7K2", "c4", "m3", "Reached the homestay, it's beautiful 😍", 2 * DAY - 8 * HOUR);
  say("CRG7K2", "c5", "m2", "Dinner was ₹2150, added it to expenses.", 5 * HOUR);
  say("GOA4X9", "c6", "m5", "Train is at 7:10 from Pune station.", 4 * DAY);
  say("GOA4X9", "c7", "m7", "I'll book the scooters.", 22 * HOUR);
  say("OTY8M5", "c8", "m10", "Toll paid, we're past Mettupalayam.", 2 * HOUR);
  say("WYD2T6", "c9", "m12", "Who's driving on Friday?", 30 * 60 * 1000);

  const devices: DeviceToken[] = [
    { token: "demo-token-asha-0000000000000001", uid: "u-asha", platform: "android", updatedAt: now - 2 * HOUR },
    { token: "demo-token-vikram-000000000000002", uid: "u-vikram", platform: "android", updatedAt: now - 6 * HOUR },
    { token: "demo-token-meera-0000000000000003", uid: "u-meera", platform: "android", updatedAt: now - 1 * DAY },
    { token: "demo-token-rahul-0000000000000004", uid: "u-rahul", platform: "android", updatedAt: now - 3 * DAY },
    { token: "demo-token-neha-00000000000000005", uid: "u-neha", platform: "android", updatedAt: now - 40 * 60 * 1000 },
  ];
  const admins: AdminEntry[] = [{ uid: "demo-admin", email: "admin@example.com", name: "Demo admin", addedAt: now - 30 * DAY }];

  const itineraries: Record<string, ItineraryDay[]> = {
    CRG7K2: [
      { id: "d1", label: "Day 1", order: 0, stops: [
        { id: "s1", fromTime: "05:30 AM", tillTime: "", place: "Bengaluru", notes: "", order: 0, source: "ROUTE_START" },
        { id: "s2", fromTime: "08:00 AM", tillTime: "08:45 AM", place: "Mysuru", notes: "Breakfast", order: 1, source: "ROUTE_PITSTOP" },
        { id: "s3", fromTime: "12:30 PM", tillTime: "", place: "Madikeri", notes: "Check in", order: 2, source: "ROUTE_END" },
      ] },
      { id: "d2", label: "Day 2", order: 1, stops: [
        { id: "s4", fromTime: "09:00 AM", tillTime: "11:00 AM", place: "Abbey Falls", notes: "", order: 0, source: "MANUAL" },
        { id: "s5", fromTime: "05:30 PM", tillTime: "", place: "Raja's Seat", notes: "Sunset", order: 1, source: "MANUAL" },
      ] },
    ],
  };

  const userTrips: Record<string, TripSummary[]> = {};
  for (const m of members) {
    if (!m.uid) continue;
    const trip = trips.find((t) => t.code === m.tripCode)!;
    (userTrips[m.uid] ??= []).push({ tripCode: trip.code, tripName: trip.groupName, memberId: m.id, role: m.role, status: trip.status, lastAccessedAt: now - (m.joinedAt % DAY) });
  }

  return { trips, users, members, expenses, chat, devices, admins, itineraries, userTrips };
}

export function createDemoSource(now = Date.now()): DataSource {
  const data = build(now);
  const listeners = new Set<(patch: Partial<AdminData>) => void>();
  const tripListeners = new Map<string, Set<(d: TripDetail) => void>>();
  const snapshot = (): Partial<AdminData> => ({
    trips: [...data.trips],
    users: [...data.users],
    members: [...data.members],
    expenses: [...data.expenses].sort((a, b) => b.createdAt - a.createdAt),
    chat: [...data.chat].sort((a, b) => b.createdAt - a.createdAt),
    devices: [...data.devices],
    admins: [...data.admins],
    ready: { trips: true, users: true, members: true, expenses: true, chat: true, devices: true, admins: true },
  });
  const tripDetail = (code: string): TripDetail => ({
    itinerary: data.itineraries[code] ?? [],
    chat: data.chat.filter((c) => c.tripCode === code).sort((a, b) => a.createdAt - b.createdAt),
    ready: true,
  });
  const publish = () => {
    listeners.forEach((l) => l(snapshot()));
    tripListeners.forEach((set, code) => set.forEach((l) => l(tripDetail(code))));
  };

  return {
    mode: "demo",
    subscribeAll(onChange) {
      listeners.add(onChange);
      onChange(snapshot());
      return () => listeners.delete(onChange);
    },
    subscribeTrip(code, onChange) {
      const set = tripListeners.get(code) ?? new Set();
      set.add(onChange);
      tripListeners.set(code, set);
      onChange(tripDetail(code));
      return () => set.delete(onChange);
    },
    subscribeUserTrips(uid, onChange) {
      onChange(data.userTrips[uid] ?? []);
      return () => undefined;
    },
    actions: {
      async setTripStatus(code, status) {
        data.trips = data.trips.map((t) => (t.code === code ? { ...t, status } : t));
        publish();
      },
      async deleteChatMessage(message) {
        data.chat = data.chat.filter((c) => !(c.id === message.id && c.tripCode === message.tripCode));
        publish();
      },
      async addAdmin({ uid, email, name }) {
        data.admins = [...data.admins.filter((a) => a.uid !== uid), { uid, email, name, addedAt: Date.now() }];
        publish();
      },
      async removeAdmin(uid) {
        data.admins = data.admins.filter((a) => a.uid !== uid);
        publish();
      },
    },
  };
}
