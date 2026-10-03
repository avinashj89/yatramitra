import type {
  AdminEntry,
  ChatMessage,
  DeviceToken,
  Expense,
  ItineraryDay,
  Member,
  Trip,
  TripStatus,
  TripSummary,
  UserProfile,
} from "../types";

export type CollectionKey = "trips" | "users" | "members" | "expenses" | "chat" | "devices" | "admins";

/** Everything the panel watches across the whole app, kept live. */
export interface AdminData {
  trips: Trip[];
  users: UserProfile[];
  members: Member[];
  expenses: Expense[];
  /** Newest Group chat messages across every trip. */
  chat: ChatMessage[];
  devices: DeviceToken[];
  admins: AdminEntry[];
  ready: Record<CollectionKey, boolean>;
  errors: Partial<Record<CollectionKey, string>>;
}

/** The extra, per-trip data shown on a trip's page. */
export interface TripDetail {
  itinerary: ItineraryDay[];
  /** The trip's whole Group chat plus its older suggestions, oldest first. */
  chat: ChatMessage[];
  ready: boolean;
  error?: string;
}

export interface AdminActions {
  setTripStatus(code: string, status: TripStatus): Promise<void>;
  deleteChatMessage(message: ChatMessage): Promise<void>;
  addAdmin(entry: { uid: string; email: string; name: string }): Promise<void>;
  removeAdmin(uid: string): Promise<void>;
}

export interface DataSource {
  mode: "live" | "demo";
  subscribeAll(onChange: (patch: Partial<AdminData>) => void): () => void;
  subscribeTrip(code: string, onChange: (detail: TripDetail) => void): () => void;
  subscribeUserTrips(uid: string, onChange: (trips: TripSummary[], error?: string) => void): () => void;
  actions: AdminActions;
}

export const emptyData: AdminData = {
  trips: [],
  users: [],
  members: [],
  expenses: [],
  chat: [],
  devices: [],
  admins: [],
  ready: { trips: false, users: false, members: false, expenses: false, chat: false, devices: false, admins: false },
  errors: {},
};

/** Plain-language versions of Firestore errors. */
export function describeError(error: unknown): string {
  const code = (error as { code?: string })?.code ?? "";
  if (code === "permission-denied") {
    return "Firestore refused access. Check that the latest firestore.rules are published and that your account is in the admins list.";
  }
  if (code === "failed-precondition") {
    return "Firestore is still building an index for this view. It's ready a few minutes after the indexes are deployed.";
  }
  if (code === "unavailable") return "Can't reach Firestore. Check your internet connection.";
  return (error as { message?: string })?.message ?? "Something went wrong loading this data.";
}
