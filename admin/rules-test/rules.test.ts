import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, collectionGroup, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where } from "firebase/firestore";

// Runs the real firestore.rules against the local Firestore emulator (npm run test:rules).
// Checks both sides: admins can see everything, and ordinary users keep exactly the access
// the Android app needs, and no more.

let env: RulesTestEnvironment;

const ADMIN = "admin-uid";
const ALICE = "alice-uid"; // Organizer of trip T1
const BOB = "bob-uid"; // signed-in user who is not on T1

beforeAll(async () => {
  const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8085").split(":");
  env = await initializeTestEnvironment({
    projectId: "demo-yatramitra",
    firestore: { rules: readFileSync(resolve(__dirname, "../../firestore.rules"), "utf8"), host, port: Number(port) },
  });
});

afterAll(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "admins", ADMIN), { email: "admin@example.com" });
    await setDoc(doc(db, "trips", "T1"), { groupName: "Goa", createdAt: 1 });
    await setDoc(doc(db, "trips", "T1", "members", "m1"), { name: "Alice", uid: ALICE, role: "ORGANIZER", joinedAt: 1 });
    await setDoc(doc(db, "trips", "T1", "expenses", "e1"), { amount: 100, createdAt: 2 });
    await setDoc(doc(db, "trips", "T1", "chat", "c1"), { text: "hi", createdAt: 3, authorUid: ALICE });
    await setDoc(doc(db, "users", ALICE), { name: "Alice", email: "alice@example.com" });
    await setDoc(doc(db, "users", ALICE, "trips", "T1"), { tripCode: "T1", memberId: "m1" });
    await setDoc(doc(db, "deviceTokens", "tok-alice"), { uid: ALICE });
  });
});

const as = (uid: string | null) => (uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore());

describe("admins", () => {
  it("can list every trip and read across all trips", async () => {
    const db = as(ADMIN);
    await assertSucceeds(getDocs(collection(db, "trips")));
    await assertSucceeds(getDocs(collectionGroup(db, "members")));
    await assertSucceeds(getDocs(collectionGroup(db, "expenses")));
    await assertSucceeds(getDocs(collectionGroup(db, "chat")));
    await assertSucceeds(getDocs(collection(db, "deviceTokens")));
    await assertSucceeds(getDocs(collection(db, "users", ALICE, "trips")));
    await assertSucceeds(getDocs(collection(db, "admins")));
  });

  it("can moderate: remove a chat message and change a trip's status", async () => {
    const db = as(ADMIN);
    await assertSucceeds(deleteDoc(doc(db, "trips", "T1", "chat", "c1")));
    await assertSucceeds(updateDoc(doc(db, "trips", "T1"), { status: "COMPLETED" }));
  });

  it("can add and remove other admins", async () => {
    const db = as(ADMIN);
    await assertSucceeds(setDoc(doc(db, "admins", BOB), { email: "bob@example.com" }));
    await assertSucceeds(deleteDoc(doc(db, "admins", BOB)));
  });
});

describe("ordinary signed-in users", () => {
  it("cannot list all trips or read across trips", async () => {
    const db = as(BOB);
    await assertFails(getDocs(collection(db, "trips")));
    await assertFails(getDocs(collectionGroup(db, "members")));
    await assertFails(getDocs(collectionGroup(db, "expenses")));
    await assertFails(getDocs(collectionGroup(db, "chat")));
    await assertFails(getDocs(collection(db, "deviceTokens")));
    await assertFails(getDocs(collection(db, "admins")));
  });

  it("cannot make themselves an admin or read someone else's admin entry", async () => {
    const db = as(BOB);
    await assertFails(setDoc(doc(db, "admins", BOB), { email: "bob@example.com" }));
    await assertFails(getDoc(doc(db, "admins", ADMIN)));
  });

  it("can check whether they themselves are an admin", async () => {
    await assertSucceeds(getDoc(doc(as(BOB), "admins", BOB)));
    await assertSucceeds(getDoc(doc(as(ADMIN), "admins", ADMIN)));
  });

  it("cannot read someone else's homepage list", async () => {
    await assertFails(getDocs(collection(as(BOB), "users", ALICE, "trips")));
  });
});

describe("what the Android app does keeps working", () => {
  it("opens a trip by code and reads and writes inside it", async () => {
    const db = as(BOB);
    await assertSucceeds(getDoc(doc(db, "trips", "T1")));
    await assertSucceeds(getDocs(collection(db, "trips", "T1", "members")));
    await assertSucceeds(getDocs(collection(db, "trips", "T1", "expenses")));
    await assertSucceeds(getDocs(collection(db, "trips", "T1", "chat")));
    await assertSucceeds(setDoc(doc(db, "trips", "T1", "chat", "c2"), { text: "hello", createdAt: 4, authorUid: BOB }));
    await assertSucceeds(setDoc(doc(db, "trips", "T1", "members", "m2"), { name: "Bob", uid: BOB, joinedAt: 5 }));
    await assertSucceeds(updateDoc(doc(db, "trips", "T1"), { routePlan: { days: [] } }));
    // Hospitals along each route day, saved for the whole group.
    await assertSucceeds(setDoc(doc(db, "trips", "T1", "hospitals", "day-0"), { dayIndex: 0, routeKey: "a|b", hospitals: [] }));
    await assertSucceeds(getDocs(collection(db, "trips", "T1", "hospitals")));
  });

  it("reads its own homepage list and finds its own place on a trip", async () => {
    await assertSucceeds(getDocs(collection(as(ALICE), "users", ALICE, "trips")));
    await assertSucceeds(getDocs(query(collection(as(ALICE), "trips", "T1", "members"), where("uid", "==", ALICE))));
  });

  it("matches companions by email (profiles stay listable to signed-in users)", async () => {
    await assertSucceeds(getDocs(query(collection(as(BOB), "users"), where("email", "in", ["alice@example.com"]))));
  });

  it("registers and removes only its own phone for notifications", async () => {
    await assertSucceeds(setDoc(doc(as(BOB), "deviceTokens", "tok-bob"), { uid: BOB }));
    await assertFails(setDoc(doc(as(BOB), "deviceTokens", "tok-x"), { uid: ALICE }));
    await assertFails(deleteDoc(doc(as(BOB), "deviceTokens", "tok-alice")));
    await assertSucceeds(deleteDoc(doc(as(ALICE), "deviceTokens", "tok-alice")));
  });
});

describe("signed-out visitors", () => {
  it("see nothing", async () => {
    const db = as(null);
    await assertFails(getDoc(doc(db, "trips", "T1")));
    await assertFails(getDocs(collection(db, "trips")));
    await assertFails(getDoc(doc(db, "admins", ADMIN)));
    await assertFails(getDocs(collectionGroup(db, "chat")));
    await assertFails(getDocs(collection(db, "trips", "T1", "hospitals")));
  });
});
