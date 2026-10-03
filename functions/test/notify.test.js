// Run with: node --test "functions/test/*.test.js"   (or `npm test` inside functions/)
const test = require("node:test");
const assert = require("node:assert/strict");
const notify = require("../notify");

test("chat message shows who wrote what, under the trip name", () => {
  const m = notify.chatMessage("Coorg weekend", "Ravi", "Leaving at 6?");
  assert.equal(m.type, "chat");
  assert.equal(m.title, "Coorg weekend");
  assert.equal(m.body, "Ravi: Leaving at 6?");
});

test("long or messy text is squashed and cut", () => {
  const m = notify.chatMessage("T", "A", "x".repeat(500) + "\n\n  y");
  assert.ok(m.body.length <= 180);
  assert.ok(m.body.endsWith("…"));
  assert.equal(notify.truncate("  a \n\n b  "), "a b");
});

test("missing names fall back to something readable", () => {
  const m = notify.chatMessage(undefined, undefined, "hi");
  assert.equal(m.title, "Your trip");
  assert.equal(m.body, "Someone: hi");
});

test("starting the trip notifies once, with who started it", () => {
  const changes = notify.tripChanges({ groupName: "Goa" }, { groupName: "Goa", startedAt: 1, startedBy: "Avi" });
  assert.equal(changes.length, 1);
  assert.equal(changes[0].type, "started");
  assert.match(changes[0].body, /^Avi started the trip/);
  // Saving the route afterwards (startedAt unchanged) is not news.
  assert.deepEqual(notify.tripChanges({ startedAt: 1, routePlan: {} }, { startedAt: 1, routePlan: { a: 1 } }), []);
});

test("completing, reopening and renaming are announced", () => {
  assert.equal(notify.tripChanges({ status: "ONGOING" }, { status: "COMPLETED" })[0].type, "completed");
  assert.equal(notify.tripChanges({ status: "COMPLETED" }, { status: "ONGOING" })[0].type, "reopened");
  const renamed = notify.tripChanges({ groupName: "Old" }, { groupName: "New" });
  assert.equal(renamed[0].type, "renamed");
  assert.equal(renamed[0].title, "New");
});

test("a brand-new trip document with no status is not mistaken for a reopen", () => {
  assert.deepEqual(notify.tripChanges({}, { groupName: "X", createdAt: 1 }), []);
});

test("joining and being added read differently", () => {
  const joined = notify.memberAdded("Goa", { name: "Sita" }, "Sita", true);
  assert.equal(joined.forOthers.body, "Sita joined the trip.");
  const added = notify.memberAdded("Goa", { name: "Ravi" }, "Avi", false);
  assert.equal(added.forOthers.body, "Avi added Ravi to the trip.");
  assert.match(added.forNewMember.body, /^Avi added you/);
});

test("expenses show the amount in rupees", () => {
  const m = notify.expenseAdded("Goa", { paidByName: "Avi", amount: 1250.5, description: "Dinner" });
  assert.equal(m.body, "Avi added ₹1,250.5 for Dinner.");
  assert.equal(notify.expenseAdded("Goa", { amount: 100 }).body, "Someone added ₹100.");
});

test("recipients drop the sender, blanks and duplicates", () => {
  assert.deepEqual(notify.recipients(["a", "b", "a", "", null, "me"], "me"), ["a", "b"]);
  assert.deepEqual(notify.recipients(["a"], null), ["a"]);
});

test("chunk splits for Firestore 'in' (30) and FCM (500) limits", () => {
  assert.deepEqual(notify.chunk([1, 2, 3, 4, 5], 2), [[1, 2], [3, 4], [5]]);
  assert.deepEqual(notify.chunk([], 30), []);
});
