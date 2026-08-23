# YatraMitra — Setup Guide (no coding required)

Everything in this folder is a finished Android app. You don't need to write or
understand any code — just follow these steps once, in order. Total cost: **₹0**.

You'll need: a computer with a web browser (for the one-time setup) and your
Android phone (to install the finished app at the end).

---

## Part 1 — Put the code on GitHub (so it can be built into an app)

1. Go to **github.com** and click **Sign up** (free). Skip this if you already have an account.
2. Once logged in, click the **+** icon (top right) → **New repository**.
3. Name it `yatramitra` (or anything you like). Leave everything else as default. Click **Create repository**.
4. On the new repo's page, click **uploading an existing file** (a blue link near the middle of the page).
5. Open the `YatraMitra` folder you downloaded from Claude on your computer, select **all files and folders inside it**, and drag them into the browser window.
   - Make sure you're uploading the *contents* of the YatraMitra folder (settings.gradle.kts, app, .github, etc.) — not the YatraMitra folder itself.
6. Scroll down and click **Commit changes**. Your code is now on GitHub.

---

## Part 2 — Create your free Firebase project (this is what keeps everyone's expenses in sync)

1. Go to **console.firebase.google.com** and sign in with any Google account.
2. Click **Create a project**. Name it anything (e.g. "YatraMitra"). You can turn off Google Analytics when asked — not needed. Click **Create project**.
3. Once it's ready, click the **Android icon** to add an Android app to the project.
4. For "Android package name" enter exactly: `com.avinash.yatramitra`
   (This must match exactly, or the app won't connect.)
5. Skip the nickname and SHA-1 fields — leave them blank. Click **Register app**.
6. Click **Download google-services.json**. Save it somewhere you can find it (e.g. Desktop).
7. Click **Next** through the remaining screens, then **Continue to console** (you don't need to add the SDK code shown — that's already done for you).

### Turn on the database

1. In the Firebase console, on the left menu, click **Build → Firestore Database**.
2. Click **Create database**. Choose any nearby location. Start in **Production mode**. Click **Enable**.
3. Once created, click the **Rules** tab at the top.
4. Delete everything in the box, and paste in the contents of the `firestore.rules` file from your YatraMitra folder (open it in Notepad/TextEdit, copy everything, paste it in).
5. Click **Publish**.

### Put your real Firebase file into the project

1. Go back to your repository on GitHub.
2. Open the `app` folder in your repo.
3. Click on `google-services.json` (this is currently a placeholder), then click the **pencil/edit icon**.
4. Delete all the placeholder text, then open the real `google-services.json` file you downloaded from Firebase (in Notepad/TextEdit), copy everything, and paste it in here instead.
5. Scroll down and click **Commit changes**.

---

## Part 3 — Let GitHub build your app (no software to install)

1. On your repository page, click the **Actions** tab.
2. You should see a workflow run already started (called "Build YatraMitra APK") — committing the file above automatically triggers it. If you don't see one, click **Build YatraMitra APK** on the left, then **Run workflow** → **Run workflow**.
3. Wait 3–5 minutes. Refresh the page — a green checkmark ✅ means it worked. A red ✗ means something needs fixing (see Troubleshooting below).
4. Click into the finished run, scroll to the bottom, and under **Artifacts** click **YatraMitra-debug-apk** to download it. It downloads as a `.zip` — open it to get `app-debug.apk`.

---

## Part 4 — Install it on your phone

1. Get `app-debug.apk` onto your Android phone (email it to yourself, use Google Drive, WhatsApp to yourself, or a USB cable — whatever's easiest).
2. Tap the file on your phone to install it. Android will warn about "installing from unknown sources" — this is normal for any app not from the Play Store. Tap **Settings** on that warning, allow installs from that source (e.g. your Files app or Chrome), then go back and tap the file again to install.
3. Open **YatraMitra** from your app drawer. Done!

---

## Using the app

- **Planner tab**: enter From/To, whether it's a round trip, how often you want a break (by km or hours), and fastest vs alternate route. Tap the button to open Google Maps with turn-by-turn directions.
- **Itinerary tab**: build a day-by-day plan — add a day, then add stops with a from/till time, place name, and a note (like "Breakfast" or "Lunch"). This is the table style from your reference screenshot.
- **Expenses tab**: tap **Create a new trip** to get a short code (e.g. `7F3K9Q`). Share that code with your travel companions (there's a share button once you're in) — they each install the app the same way and enter that code plus their name to join. Everyone then sees the same live expense list and who-owes-whom, Splitwise-style.

## Costs, honestly

- **GitHub**: free for this use (public repos get unlimited free build minutes; private repos get 2,000 free minutes/month, and one build uses only a few minutes).
- **Firebase**: free "Spark" plan covers 50,000 reads and 20,000 writes a day — a personal trip app won't come close to that.
- You will never be asked for a credit card for either of these unless you deliberately upgrade a plan.

## If you want changes later

Come back to this Claude conversation (or start a new one and mention this project) and describe what you'd like changed in plain English — new fields, colors, features, anything. I'll update the code; you just repeat Part 1 (upload the changed files) and Part 3 (download the new APK) — Part 2 (Firebase) only needs doing once.

## Troubleshooting

- **Red ✗ on the GitHub Action**: click into the run and open the "Build debug APK" step to see the error. The most common cause is `google-services.json` not being replaced correctly, or the package name not matching `com.avinash.yatramitra` exactly in Firebase. Paste the error into this Claude conversation and I'll fix it.
- **"App not installed" on your phone**: make sure you downloaded `app-debug.apk` itself (not the `.zip` it came in), and that your phone has a few hundred MB of free storage.
- **Expenses don't sync between phones**: double-check both phones used the exact same trip code, and that you completed the Firebase steps in Part 2 (a placeholder `google-services.json` will make the app open fine but expenses won't save).
