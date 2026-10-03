import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import config from "./firebase-config.json";

// The "YatraMitra Admin" web app registered in the same Firebase project as the Android app.
// Web config values are identifiers, not secrets: access is controlled by Firestore rules.
export const firebaseApp = initializeApp({
  apiKey: config.apiKey,
  authDomain: config.authDomain,
  projectId: config.projectId,
  storageBucket: config.storageBucket,
  messagingSenderId: config.messagingSenderId,
  appId: config.appId,
});

export const auth = getAuth(firebaseApp);
export const db = getFirestore(firebaseApp);
