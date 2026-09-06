# YatraMitra — Setup Guide (no coding required)

You already have this app set up on GitHub and Firebase. This guide is now mostly
about **updating** the app when Claude sends you new code — the from-scratch setup
further down only matters again if you ever start over on a brand new GitHub repo.

---

## Updating the app with new changes (this is what you'll use most)

Whenever Claude finishes a batch of changes, it will tell you one of two things:

**A) "Replace this one file"** (small fixes, like a single bug)

1. Go to your repo on GitHub and open the exact file path Claude names.
2. Click the pencil (edit) icon.
3. Either make the small edit Claude describes, or select all and paste in the full replacement content Claude gives you.
4. Scroll down, click **Commit changes**. This alone triggers a new build.

**B) "Bulk re-upload"** (bigger batches touching many files, like a round of UI changes)

1. Go to your repo → **Add file → Upload files**.
2. Open the new YatraMitra folder Claude sent you, select everything inside it, and drag it into the browser window — same as your very first upload.
3. **Before clicking Commit**, GitHub shows a list of every file about to be uploaded. Find `app/google-services.json` in that list and click the small ✕ to remove it from the upload. That file is your real Firebase config — you set it up once in Part 2 below, and you never want a new upload to overwrite it with Claude's placeholder.
4. Click **Commit changes**.

Either way, that commit automatically starts a new build. Then:

5. Click the **Actions** tab and watch for the new run. Wait 3–5 minutes; a green ✅ means it worked, a red ✗ means something needs fixing (see Troubleshooting below — send Claude the `e: file` error line).
6. Click into the finished run, scroll to **Artifacts**, and download **YatraMitra-debug-apk**. Unzip it to get `app-debug.apk`.
7. Get that file onto your phone (or straight into BlueStacks/BlueStacks Air if you're testing on your computer) and install it, replacing the old version.

You do **not** need to touch Firebase, GitHub repo settings, or `google-services.json` again for a routine update — that's all one-time setup, done below.

---

## First-time setup (already done — reference only)

Skip this whole section unless you're setting the app up again from a brand new, empty GitHub repo (e.g. on a different account).

### Part 1 — Put the code on GitHub

1. Go to **github.com** and click **Sign up** (free), if you don't have an account.
2. Click the **+** icon (top right) → **New repository**. Name it anything. Click **Create repository**.
3. On the new repo's page, click **uploading an existing file**.
4. Select everything inside the YatraMitra folder and drag it in — the *contents* of the folder (settings.gradle.kts, app, .github, etc.), not the folder itself.
5. Click **Commit changes**.

### Part 2 — Create your free Firebase project

1. Go to **console.firebase.google.com**, sign in, click **Create a project**. Turn off Google Analytics when asked. Click **Create project**.
2. Click the **Android icon** to add an Android app. For "Android package name" enter exactly: `com.avinash.yatramitra`
3. Skip nickname/SHA-1. Click **Register app**, then **Download google-services.json**.
4. Click through to **Continue to console**.
5. Left menu → **Build → Firestore Database** → **Create database** → any nearby location → **Production mode** → **Enable**.
6. Click the **Rules** tab, delete everything there, paste in the contents of `firestore.rules` from your YatraMitra folder, click **Publish**.
7. Left menu → **Build → Authentication** → **Get started** (if shown) → **Sign-in method** tab → click **Anonymous** → toggle **Enable** → **Save**. The app signs everyone in anonymously automatically, and every trip/route/itinerary/expense action needs this enabled or you'll see "Couldn't create a trip" / "Couldn't join" errors.
8. Back in your GitHub repo, open `app/google-services.json`, click the pencil icon, delete the placeholder content, paste in your real downloaded file's content instead, and **Commit changes**.

From here on, use the "Updating the app" section above for every future change.

---

## Using the app

Opening the app now always starts with **Create a new trip** (you become its *Organizer*) or **enter a trip code** you were given (you join as a *Joiner*) — every tab lives inside that one shared trip and stays live-synced to everyone else in it. A shared bar above the tabs always shows your role, an Invite action (shares the trip code), and a live-sync indicator.

- **Route & Stops tab** (Organizer): trip name, From/To with place suggestions as you type, up to 4 stops with a "+" to add more, round trip checkbox, and a Smart Pitstop Engine (break-frequency presets, preference chips, and "Generate pitstops for Itinerary" to auto-fill Day 1 with real break-stop suggestions along your route). Also lists trip members and lets you add people directly by name (they don't need to install the app). Tap "Open in Google Maps" for turn-by-turn directions.
- **Route & Stops tab** (Joiner): a read-only summary of the Organizer's route, plus a box to send them a suggested change.
- **Itinerary tab**: day-by-day plan, one day at a time via the chips at the top. Rows generated from the Route tab (start point, pitstops, destination) are locked — only their time and notes can be edited — while rows you add yourself are fully editable. Organizer-only editing; Joiners see it read-only and can submit suggestions. "Save & notify group" shares a summary of the day to WhatsApp.
- **Expenses tab**: add expenses split equally, by percentage, or by exact amount; see your real share and whether you're owed or owe money, plus settle-up suggestions. Add your UPI ID (optional) so others can pay you directly with a "Pay via UPI" button.
- Joiners can suggest changes to the route or itinerary from within those tabs; the Organizer sees them and can Accept or Dismiss.

## Costs, honestly

- **GitHub**: free for this use (public repos get unlimited free build minutes; private repos get 2,000 free minutes/month, and one build uses only a few minutes).
- **Firebase**: free "Spark" plan covers 50,000 reads and 20,000 writes a day — a personal trip app won't come close to that.
- **OpenStreetMap/OSRM/Overpass** (place suggestions and pitstop-finder): free public services, no account needed.
- You will never be asked for a credit card for any of these unless you deliberately upgrade a plan.

## Troubleshooting

- **Red ✗ on the GitHub Action**: click into the run and open the "Build debug APK" step, search the log for `e: file`, and send Claude those lines — that's the actual error, not the big stack trace above it.
- **"App not installed" on your phone**: first, make sure you downloaded `app-debug.apk` itself (not the `.zip` it came in) and that your phone has a few hundred MB of free storage. If those are fine, it's almost always a signing-key mismatch with whatever version is already on your phone — **uninstall YatraMitra from the phone first, then install the new APK.** The workflow caches one debug signing key across builds specifically to prevent this, but the very first build after that caching was added (or after a "Clear cache" done manually on GitHub) will still differ from whatever you already have installed.
- **"Couldn't create a trip" / "Couldn't join" right after opening the app**: almost always means Anonymous sign-in isn't enabled — Firebase Console → **Build → Authentication → Sign-in method** → make sure **Anonymous** is enabled (see Part 2, step 7). Also double-check the **Rules** tab under Firestore Database shows the latest contents of `firestore.rules` and that you clicked **Publish** after pasting.
- **Expenses don't sync between phones**: double-check both phones used the exact same trip code, and that your real `google-services.json` (not the placeholder) is committed in `app/`.
- **Pitstop suggestions come back empty**: the free map services occasionally have no data for very remote areas, or a typed place couldn't be found — try a slightly more specific place name, or check your internet connection.
