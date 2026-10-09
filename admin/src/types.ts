// The shapes of YatraMitra's Firestore data, as the Android app writes them
// (see app/src/main/java/com/avinash/yatramitra/model/TripModels.kt and data/TripRepository.kt).

export type TripStatus = "ONGOING" | "COMPLETED";
export type MemberRole = "ORGANIZER" | "JOINER";

export interface RouteDay {
  from: string;
  toStops: string[];
  roundTrip: boolean;
  /** Pitstops are switched on or off per day. */
  pitstopsEnabled: boolean;
}

export interface RoutePlan {
  days: RouteDay[];
  breakEvery: string;
  breakUnit: "KM" | "HOURS";
  pitstopCategories: string[];
}

/** trips/{code} */
export interface Trip {
  code: string;
  groupName: string;
  status: TripStatus;
  createdAt: number;
  startedAt: number;
  startedBy: string;
  routePlan: RoutePlan | null;
}

/** trips/{code}/members/{id} */
export interface Member {
  id: string;
  tripCode: string;
  name: string;
  role: MemberRole;
  phone: string;
  email: string;
  uid: string | null;
  upiId: string;
  joinedAt: number;
}

/** trips/{code}/expenses/{id} */
export interface Expense {
  id: string;
  tripCode: string;
  description: string;
  amount: number;
  paidByMemberId: string;
  paidByName: string;
  splitAmongMemberIds: string[];
  customSplitAmounts: Record<string, number>;
  createdAt: number;
}

/** trips/{code}/chat/{id}, plus the older routeSuggestions / itinerarySuggestions. */
export interface ChatMessage {
  id: string;
  tripCode: string;
  authorMemberId: string;
  authorName: string;
  text: string;
  createdAt: number;
  /** "chat" for Group chat; the older suggestion collections keep their own name. */
  source: "chat" | "routeSuggestions" | "itinerarySuggestions";
  /** Set when the message is an SOS from the app. */
  sos: SosAlert | null;
}

export type SosType = "ACCIDENT" | "MEDICAL" | "BREAKDOWN" | "POLICE" | "FUEL" | "ATM";

export interface SosAlert {
  type: SosType;
  lat: number | null;
  lng: number | null;
  locationName: string;
}

export interface ItineraryStop {
  id: string;
  fromTime: string;
  tillTime: string;
  place: string;
  notes: string;
  order: number;
  source: string;
}

/** trips/{code}/itineraryDays/{id} */
export interface ItineraryDay {
  id: string;
  label: string;
  order: number;
  stops: ItineraryStop[];
}

/** users/{uid} */
export interface UserProfile {
  uid: string;
  name: string;
  email: string;
  phone: string;
}

/** users/{uid}/trips/{code}: one row of someone's homepage list. */
export interface TripSummary {
  tripCode: string;
  tripName: string;
  memberId: string;
  role: MemberRole;
  status: TripStatus;
  lastAccessedAt: number;
}

/** deviceTokens/{token}: a phone that receives this account's notifications. */
export interface DeviceToken {
  token: string;
  uid: string;
  platform: string;
  updatedAt: number;
}

/** admins/{uid}: accounts allowed into this panel. */
export interface AdminEntry {
  uid: string;
  email: string;
  name: string;
  addedAt: number;
}

export interface Balance {
  memberId: string;
  name: string;
  net: number;
}

export interface Settlement {
  fromName: string;
  toName: string;
  amount: number;
}

/** One line of the live activity feed. */
export interface ActivityEvent {
  id: string;
  kind: "trip-created" | "trip-started" | "member-joined" | "expense" | "chat";
  at: number;
  tripCode: string;
  tripName: string;
  text: string;
}
