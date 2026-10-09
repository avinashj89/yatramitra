// YatraMitra server functions: turn changes in Firestore into push notifications for everyone
// on the trip (except the person who made the change).
//
// Who gets them: every account that is a member of the trip (trips/{code}/members with a uid).
// That is the same set of people who have the trip in their homepage list.
// Which phones: deviceTokens/{token} documents, written by the app on sign-in and removed on
// sign-out. Tokens that Firebase reports as dead are deleted here.
//
// Deploy (needs the Blaze plan): firebase deploy --only functions

const { onDocumentCreatedWithAuthContext, onDocumentUpdatedWithAuthContext } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const logger = require("firebase-functions/logger");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const notify = require("./notify");

// Must match the Firestore database location: the (default) database is in asia-south2 (Delhi).
const REGION = "asia-south2";

setGlobalOptions({ region: REGION, maxInstances: 5 });
initializeApp();
const db = getFirestore();

async function tripName(code) {
  const snap = await db.doc(`trips/${code}`).get();
  return snap.exists ? snap.get("groupName") || "Your trip" : null;
}

async function memberDocs(code) {
  return (await db.collection(`trips/${code}/members`).get()).docs;
}

async function memberUids(code) {
  return (await memberDocs(code)).map((d) => d.get("uid")).filter(Boolean);
}

async function nameOfAccount(code, uid) {
  if (!uid) return null;
  const match = (await memberDocs(code)).find((d) => d.get("uid") === uid);
  return match ? match.get("name") : null;
}

/** Sends one notification to every phone of every account in [uids]. */
async function pushTo(uids, tripCode, message) {
  if (uids.length === 0) return;
  const tokens = [];
  for (const group of notify.chunk(uids, 30)) {
    const snap = await db.collection("deviceTokens").where("uid", "in", group).get();
    snap.forEach((d) => tokens.push({ token: d.id, uid: d.get("uid") }));
  }
  if (tokens.length === 0) return;

  for (const batch of notify.chunk(tokens, 500)) {
    const response = await getMessaging().sendEach(
      batch.map((t) => ({
        token: t.token,
        // Data-only, so the app builds the notification itself and can open the right trip.
        data: { type: message.type, tripCode, title: message.title, body: message.body, uid: t.uid },
        android: { priority: "high" },
      }))
    );
    const dead = [];
    response.responses.forEach((r, i) => {
      const code = r.error && r.error.code;
      if (code === "messaging/registration-token-not-registered" || code === "messaging/invalid-registration-token") {
        dead.push(batch[i].token);
      } else if (r.error) {
        logger.warn("push failed", { code, tripCode });
      }
    });
    await Promise.all(dead.map((t) => db.doc(`deviceTokens/${t}`).delete().catch(() => undefined)));
  }
}

exports.notifyChatMessage = onDocumentCreatedWithAuthContext("trips/{code}/chat/{messageId}", async (event) => {
  const data = event.data && event.data.data();
  if (!data) return;
  const code = event.params.code;
  const name = await tripName(code);
  if (name === null) return;
  // The app writes authorUid; event.authId (the signed-in writer) is only a fallback.
  const actor = data.authorUid || event.authId || null;
  const message = notify.chatMessage(name, data.authorName, data.text, data.kind);
  await pushTo(notify.recipients(await memberUids(code), actor), code, message);
});

exports.notifyTripChanged = onDocumentUpdatedWithAuthContext("trips/{code}", async (event) => {
  const before = event.data && event.data.before.data();
  const after = event.data && event.data.after.data();
  // Route edits update this document all the time; only the changes below notify anyone.
  const changes = notify.tripChanges(before, after);
  if (changes.length === 0) return;
  const code = event.params.code;
  const all = await memberUids(code);
  for (const message of changes) {
    const actor = (message.type === "started" && after.startedByUid) || event.authId || null;
    await pushTo(notify.recipients(all, actor), code, message);
  }
});

exports.notifyMemberAdded = onDocumentCreatedWithAuthContext("trips/{code}/members/{memberId}", async (event) => {
  const member = event.data && event.data.data();
  if (!member) return;
  const code = event.params.code;
  const actor = member.addedByUid || event.authId || null;
  // The Organizer joining the trip they just created isn't news to anyone.
  if (member.role === "ORGANIZER" && member.uid && member.uid === actor) return;
  const name = await tripName(code);
  if (name === null) return;
  const joinedThemselves = Boolean(member.uid) && member.uid === actor;
  const adderName = joinedThemselves ? member.name : await nameOfAccount(code, actor);
  const messages = notify.memberAdded(name, member, adderName, joinedThemselves);

  if (member.uid && !joinedThemselves) await pushTo([member.uid], code, messages.forNewMember);
  const others = notify.recipients(await memberUids(code), actor).filter((u) => u !== member.uid);
  await pushTo(others, code, messages.forOthers);
});

exports.notifyExpenseAdded = onDocumentCreatedWithAuthContext("trips/{code}/expenses/{expenseId}", async (event) => {
  const expense = event.data && event.data.data();
  if (!expense) return;
  const code = event.params.code;
  const name = await tripName(code);
  if (name === null) return;
  const actor = expense.createdByUid || event.authId || null;
  await pushTo(notify.recipients(await memberUids(code), actor), code, notify.expenseAdded(name, expense));
});
