import type {
  AdminEntry,
  ChatMessage,
  DeviceToken,
  Expense,
  ItineraryDay,
  ItineraryStop,
  Member,
  MemberRole,
  RouteDay,
  RoutePlan,
  Trip,
  TripStatus,
  TripSummary,
  UserProfile,
} from "../types";

// Turns raw Firestore document data into typed objects, tolerating missing or odd fields the
// same way the Android app does (older documents lack newer fields). Pure, so it is unit-tested.

type Data = Record<string, unknown>;

const str = (v: unknown): string => (typeof v === "string" ? v : "");
const num = (v: unknown): number => (typeof v === "number" && Number.isFinite(v) ? v : 0);
const bool = (v: unknown, fallback: boolean): boolean => (typeof v === "boolean" ? v : fallback);
const strList = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);

/** Firestore Timestamps (if any) or plain millisecond numbers, as the app stores them. */
export function millis(v: unknown): number {
  if (typeof v === "number") return v;
  if (v && typeof v === "object" && "toMillis" in v && typeof (v as { toMillis: unknown }).toMillis === "function") {
    return (v as { toMillis: () => number }).toMillis();
  }
  return 0;
}

const status = (v: unknown): TripStatus => (v === "COMPLETED" ? "COMPLETED" : "ONGOING");
const role = (v: unknown): MemberRole => (v === "ORGANIZER" ? "ORGANIZER" : "JOINER");

function routeDay(d: Data): RouteDay {
  const toStops = strList(d.toStops);
  return { from: str(d.from), toStops: toStops.length > 0 ? toStops : [""], roundTrip: bool(d.roundTrip, false) };
}

/** Mirrors RoutePlans.fromMap: multi-day routes, or an older single route as Day 1. */
export function parseRoutePlan(raw: unknown): RoutePlan | null {
  if (!raw || typeof raw !== "object") return null;
  const d = raw as Data;
  const savedDays = Array.isArray(d.days)
    ? d.days.filter((x): x is Data => !!x && typeof x === "object").map(routeDay)
    : [];
  return {
    days: savedDays.length > 0 ? savedDays : [routeDay(d)],
    breakEvery: str(d.breakEvery),
    breakUnit: d.breakUnit === "KM" ? "KM" : "HOURS",
    pitstopsEnabled: bool(d.pitstopsEnabled, true),
    pitstopCategories: strList(d.pitstopCategories),
  };
}

export function parseTrip(code: string, d: Data): Trip {
  return {
    code,
    groupName: str(d.groupName) || "Our trip",
    status: status(d.status),
    createdAt: millis(d.createdAt),
    startedAt: millis(d.startedAt),
    startedBy: str(d.startedBy),
    routePlan: parseRoutePlan(d.routePlan),
  };
}

export function parseMember(tripCode: string, id: string, d: Data): Member {
  return {
    id,
    tripCode,
    name: str(d.name) || "Unnamed",
    role: role(d.role),
    phone: str(d.phone),
    email: str(d.email),
    uid: typeof d.uid === "string" && d.uid ? d.uid : null,
    upiId: str(d.upiId),
    joinedAt: millis(d.joinedAt),
  };
}

function numberMap(v: unknown): Record<string, number> {
  if (!v || typeof v !== "object") return {};
  const out: Record<string, number> = {};
  for (const [k, value] of Object.entries(v as Data)) {
    if (typeof value === "number" && Number.isFinite(value)) out[k] = value;
  }
  return out;
}

export function parseExpense(tripCode: string, id: string, d: Data): Expense {
  return {
    id,
    tripCode,
    description: str(d.description),
    amount: num(d.amount),
    paidByMemberId: str(d.paidByMemberId),
    paidByName: str(d.paidByName),
    splitAmongMemberIds: strList(d.splitAmongMemberIds),
    customSplitAmounts: numberMap(d.customSplitAmounts),
    createdAt: millis(d.createdAt),
  };
}

export function parseChat(tripCode: string, id: string, d: Data, source: ChatMessage["source"] = "chat"): ChatMessage {
  return {
    id,
    tripCode,
    authorMemberId: str(d.authorMemberId),
    authorName: str(d.authorName) || "Someone",
    text: str(d.text),
    createdAt: millis(d.createdAt),
    source,
  };
}

function parseStop(d: Data): ItineraryStop {
  return {
    id: str(d.id),
    fromTime: str(d.fromTime),
    tillTime: str(d.tillTime),
    place: str(d.place),
    notes: str(d.notes),
    order: num(d.order),
    source: str(d.source) || "MANUAL",
  };
}

export function parseItineraryDay(id: string, d: Data): ItineraryDay {
  const stops = Array.isArray(d.stops)
    ? d.stops.filter((x): x is Data => !!x && typeof x === "object").map(parseStop).sort((a, b) => a.order - b.order)
    : [];
  return { id, label: str(d.label) || "Day", order: num(d.order), stops };
}

export function parseUser(uid: string, d: Data): UserProfile {
  return { uid, name: str(d.name) || "Traveler", email: str(d.email), phone: str(d.phone) };
}

export function parseTripSummary(code: string, d: Data): TripSummary {
  return {
    tripCode: str(d.tripCode) || code,
    tripName: str(d.tripName),
    memberId: str(d.memberId),
    role: role(d.role),
    status: status(d.status),
    lastAccessedAt: millis(d.lastAccessedAt),
  };
}

export function parseDevice(token: string, d: Data): DeviceToken {
  return { token, uid: str(d.uid), platform: str(d.platform) || "android", updatedAt: millis(d.updatedAt) };
}

export function parseAdmin(uid: string, d: Data): AdminEntry {
  return { uid, email: str(d.email), name: str(d.name), addedAt: millis(d.addedAt) };
}
