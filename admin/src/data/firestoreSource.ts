import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  limit,
  limitToLast,
  onSnapshot,
  orderBy,
  query,
  setDoc,
  updateDoc,
  type DocumentData,
  type Query,
  type QueryDocumentSnapshot,
  type Unsubscribe,
} from "firebase/firestore";
import { auth, db } from "../firebase/app";
import { parseAdmin, parseChat, parseDevice, parseExpense, parseItineraryDay, parseMember, parseTrip, parseTripSummary, parseUser } from "../lib/parse";
import type { ChatMessage } from "../types";
import { describeError, type AdminData, type CollectionKey, type DataSource, type TripDetail } from "./source";

/** The trip code a document inside trips/{code}/... belongs to. */
const tripCodeOf = (d: QueryDocumentSnapshot) => d.ref.parent.parent?.id ?? "";

const MAX_MEMBERS = 5000;
const MAX_EXPENSES = 2000;
const MAX_CHAT = 500;

/**
 * Newest-first query across every trip; if Firestore's index for it is still being built
 * (right after deploying), falls back to an unordered read sorted here, so the page still works.
 */
function watchNewest(
  group: string,
  max: number,
  onDocs: (docs: QueryDocumentSnapshot<DocumentData>[]) => void,
  onError: (error: unknown) => void
): Unsubscribe {
  let fallback: Unsubscribe | null = null;
  const primary = onSnapshot(
    query(collectionGroup(db, group), orderBy("createdAt", "desc"), limit(max)),
    (snap) => onDocs(snap.docs),
    (error) => {
      if ((error as { code?: string }).code === "failed-precondition" && !fallback) {
        fallback = onSnapshot(
          query(collectionGroup(db, group), limit(max)),
          (snap) => onDocs([...snap.docs].sort((a, b) => Number(b.get("createdAt") ?? 0) - Number(a.get("createdAt") ?? 0))),
          onError
        );
      } else {
        onError(error);
      }
    }
  );
  return () => {
    primary();
    fallback?.();
  };
}

export const firestoreSource: DataSource = {
  mode: "live",

  subscribeAll(onChange) {
    const fail = (key: CollectionKey) => (error: unknown) =>
      onChange({ errors: { [key]: describeError(error) }, ready: { [key]: true } as AdminData["ready"] });
    const ok = (key: CollectionKey, patch: Partial<AdminData>) =>
      onChange({ ...patch, ready: { [key]: true } as AdminData["ready"], errors: { [key]: undefined } });

    const watch = (key: CollectionKey, q: Query, map: (snapDocs: QueryDocumentSnapshot[]) => Partial<AdminData>) =>
      onSnapshot(q, (snap) => ok(key, map(snap.docs)), fail(key));

    const subs: Unsubscribe[] = [
      watch("trips", collection(db, "trips"), (docs) => ({ trips: docs.map((d) => parseTrip(d.id, d.data())) })),
      watch("users", collection(db, "users"), (docs) => ({ users: docs.map((d) => parseUser(d.id, d.data())) })),
      watch("members", query(collectionGroup(db, "members"), limit(MAX_MEMBERS)), (docs) => ({
        members: docs.map((d) => parseMember(tripCodeOf(d), d.id, d.data())),
      })),
      watch("devices", collection(db, "deviceTokens"), (docs) => ({ devices: docs.map((d) => parseDevice(d.id, d.data())) })),
      watch("admins", collection(db, "admins"), (docs) => ({ admins: docs.map((d) => parseAdmin(d.id, d.data())) })),
      watchNewest("expenses", MAX_EXPENSES, (docs) => ok("expenses", { expenses: docs.map((d) => parseExpense(tripCodeOf(d), d.id, d.data())) }), fail("expenses")),
      watchNewest("chat", MAX_CHAT, (docs) => ok("chat", { chat: docs.map((d) => parseChat(tripCodeOf(d), d.id, d.data())) }), fail("chat")),
    ];
    return () => subs.forEach((unsubscribe) => unsubscribe());
  },

  subscribeTrip(code, onChange) {
    const state: TripDetail = { itinerary: [], chat: [], ready: false };
    const parts = { itinerary: false, chat: false, routeSuggestions: false, itinerarySuggestions: false };
    let chat: ChatMessage[] = [];
    let route: ChatMessage[] = [];
    let itinerarySuggestions: ChatMessage[] = [];
    const emit = () => {
      state.chat = [...chat, ...route, ...itinerarySuggestions].sort((a, b) => a.createdAt - b.createdAt || a.id.localeCompare(b.id));
      state.ready = Object.values(parts).every(Boolean);
      onChange({ ...state });
    };
    const fail = (error: unknown) => {
      state.error = describeError(error);
      state.ready = true;
      onChange({ ...state });
    };
    const trip = doc(db, "trips", code);
    const subs: Unsubscribe[] = [
      onSnapshot(collection(trip, "itineraryDays"), (snap) => {
        state.itinerary = snap.docs.map((d) => parseItineraryDay(d.id, d.data())).sort((a, b) => a.order - b.order);
        parts.itinerary = true;
        emit();
      }, fail),
      onSnapshot(query(collection(trip, "chat"), orderBy("createdAt", "asc"), limitToLast(MAX_CHAT)), (snap) => {
        chat = snap.docs.map((d) => parseChat(code, d.id, d.data()));
        parts.chat = true;
        emit();
      }, fail),
      onSnapshot(collection(trip, "routeSuggestions"), (snap) => {
        route = snap.docs.map((d) => parseChat(code, d.id, d.data(), "routeSuggestions"));
        parts.routeSuggestions = true;
        emit();
      }, fail),
      onSnapshot(collection(trip, "itinerarySuggestions"), (snap) => {
        itinerarySuggestions = snap.docs.map((d) => parseChat(code, d.id, d.data(), "itinerarySuggestions"));
        parts.itinerarySuggestions = true;
        emit();
      }, fail),
    ];
    return () => subs.forEach((unsubscribe) => unsubscribe());
  },

  subscribeUserTrips(uid, onChange) {
    return onSnapshot(
      collection(db, "users", uid, "trips"),
      (snap) => onChange(snap.docs.map((d) => parseTripSummary(d.id, d.data())).sort((a, b) => b.lastAccessedAt - a.lastAccessedAt)),
      (error) => onChange([], describeError(error))
    );
  },

  actions: {
    async setTripStatus(code, status) {
      await updateDoc(doc(db, "trips", code), { status });
    },
    async deleteChatMessage(message) {
      await deleteDoc(doc(db, "trips", message.tripCode, message.source, message.id));
    },
    async addAdmin({ uid, email, name }) {
      await setDoc(doc(db, "admins", uid), { email, name, addedAt: Date.now(), addedBy: auth.currentUser?.uid ?? null });
    },
    async removeAdmin(uid) {
      await deleteDoc(doc(db, "admins", uid));
    },
  },
};
