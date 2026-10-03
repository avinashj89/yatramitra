# YatraMitra Admin

A web admin panel that watches the whole YatraMitra app live from Cloud Firestore.

## Run it

```
cd admin
npm install
npm run dev          # http://localhost:5174
```

Open `http://localhost:5174/?demo` to look around with made-up data, without signing in.

## Who can sign in

Only accounts with a document at `admins/{uid}` in Firestore (see `../firestore.rules`). The first
time, sign in, copy the UID the panel shows you, and in Firebase Console create the collection
`admins` with a document whose ID is that UID and a field `email`. After that, admins can add or
remove other admins from the Admins page.

## Pages

| Page | What it shows |
| --- | --- |
| Dashboard | Live counts (users, trips by stage, people, ₹ logged, chat, phones), 30-day charts, live activity feed |
| Trips | Every trip with status, organizer, people, route days, spend; CSV export |
| Trip detail | Day-by-day route, itinerary, people (with or without the app), expenses with balances and settle-up, the Group chat; mark completed / reopen; remove messages |
| Users | Every account, their trips, their homepage list and their notification phones |
| Group chat | Every trip's messages in one stream, filter and search, remove messages |
| Expenses | Every expense across trips; CSV export |
| Devices & notifications | Which phones get push notifications for which account |
| Admins | Add or remove admins |

## How it's built

React 19 + TypeScript (Vite), MUI 7 (Material UI) with MUI X Data Grid and Charts, Tailwind CSS 4
for layout (MUI's styles sit in a CSS layer below Tailwind's utilities), Firebase JS SDK 12 with
live `onSnapshot` listeners. Data access is behind one interface (`src/data/source.ts`) with a
Firestore implementation and the demo one.

Reads across all trips use collection-group queries, which the security rules allow for admins
only, and which need the indexes in `../firestore.indexes.json` (`firebase deploy --only
firestore:indexes`). Views are capped (5,000 people, 2,000 expenses, 500 chat messages) to keep
reads small; raise the limits in `src/data/firestoreSource.ts` if the app grows.

## Tests

```
npm test             # balances, parsing, dashboard maths
npm run test:rules   # firestore.rules against the local emulator (needs Java 17+)
npm run build        # type-check + production build
```
