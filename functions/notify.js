// Builds the text of every YatraMitra push notification. Plain JavaScript with no Firebase
// imports, so it is covered by `node --test` (see test/notify.test.js) without any cloud access.

const MAX_BODY = 180;

function truncate(text, max = MAX_BODY) {
  const clean = String(text || "").replace(/\s+/g, " ").trim();
  return clean.length > max ? clean.slice(0, max - 1).trimEnd() + "…" : clean;
}

function tripTitle(tripName) {
  return truncate(tripName || "Your trip", 60);
}

/** A new Group chat message. */
function chatMessage(tripName, authorName, text) {
  return {
    type: "chat",
    title: tripTitle(tripName),
    body: truncate(`${authorName || "Someone"}: ${text || ""}`),
  };
}

/** What changed on the trip document that people should hear about (empty if nothing did). */
function tripChanges(before, after) {
  const b = before || {};
  const a = after || {};
  const title = tripTitle(a.groupName || b.groupName);
  const out = [];
  if (!b.startedAt && a.startedAt) {
    out.push({ type: "started", title, body: `${a.startedBy || "The Organizer"} started the trip! Have a great journey.` });
  }
  if (b.status !== "COMPLETED" && a.status === "COMPLETED") {
    out.push({ type: "completed", title, body: "The trip was marked completed. Settle up any expenses that are left." });
  }
  if (b.status === "COMPLETED" && a.status !== "COMPLETED") {
    out.push({ type: "reopened", title, body: "The trip was reopened for editing." });
  }
  if (b.groupName && a.groupName && b.groupName !== a.groupName) {
    out.push({ type: "renamed", title, body: truncate(`The trip was renamed from "${b.groupName}" to "${a.groupName}".`) });
  }
  return out;
}

/** Someone was added to the trip, or joined it: one message for them, one for everyone else. */
function memberAdded(tripName, member, adderName, joinedThemselves) {
  const title = tripTitle(tripName);
  const name = member.name || "Someone";
  return {
    forNewMember: { type: "added", title, body: `${adderName || "Someone"} added you to this trip. Tap to open it.` },
    forOthers: {
      type: "member",
      title,
      body: joinedThemselves ? `${name} joined the trip.` : `${adderName || "Someone"} added ${name} to the trip.`,
    },
  };
}

function formatRupees(amount) {
  const n = Number(amount) || 0;
  const rounded = Math.round(n * 100) / 100;
  return "₹" + rounded.toLocaleString("en-IN", { maximumFractionDigits: 2 });
}

/** A new shared expense. */
function expenseAdded(tripName, expense) {
  const who = expense.paidByName || "Someone";
  const what = expense.description ? ` for ${expense.description}` : "";
  return { type: "expense", title: tripTitle(tripName), body: truncate(`${who} added ${formatRupees(expense.amount)}${what}.`) };
}

function chunk(list, size) {
  const out = [];
  for (let i = 0; i < list.length; i += size) out.push(list.slice(i, i + size));
  return out;
}

/** Distinct account ids, without the person who caused the event. */
function recipients(uids, exceptUid) {
  return [...new Set(uids.filter((u) => typeof u === "string" && u.length > 0 && u !== exceptUid))];
}

module.exports = { truncate, chatMessage, tripChanges, memberAdded, expenseAdded, formatRupees, chunk, recipients };
