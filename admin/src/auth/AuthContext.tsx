import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut as firebaseSignOut,
  type User,
} from "firebase/auth";
import { doc, onSnapshot } from "firebase/firestore";
import { auth, db } from "../firebase/app";
import { describeError } from "../data/source";

export type AuthStatus = "loading" | "signed-out" | "checking" | "denied" | "admin";

export interface Viewer {
  uid: string;
  name: string;
  email: string;
  photoURL: string | null;
}

interface AuthState {
  status: AuthStatus;
  viewer: Viewer | null;
  /** Why access was refused, when Firestore said more than "not an admin". */
  error: string | null;
  demo: boolean;
  signInWithGoogle(): Promise<void>;
  signInWithEmail(email: string, password: string): Promise<void>;
  signOut(): Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

const toViewer = (u: User): Viewer => ({ uid: u.uid, name: u.displayName ?? "", email: u.email ?? u.phoneNumber ?? "", photoURL: u.photoURL });

/** ?demo in the address (or the "Explore with demo data" button) runs on made-up data. */
export function isDemoMode(): boolean {
  if (typeof window === "undefined") return false;
  if (new URLSearchParams(window.location.search).has("demo")) {
    sessionStorage.setItem("ym-admin-demo", "1");
    return true;
  }
  return sessionStorage.getItem("ym-admin-demo") === "1";
}

export function leaveDemoMode() {
  sessionStorage.removeItem("ym-admin-demo");
  window.location.href = window.location.pathname.split("?")[0];
}

export function AuthProvider({ demo, children }: { demo: boolean; children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>(demo ? "admin" : "loading");
  const [viewer, setViewer] = useState<Viewer | null>(
    demo ? { uid: "demo-admin", name: "Demo admin", email: "admin@example.com", photoURL: null } : null
  );
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (demo) return;
    let stopAdminWatch: (() => void) | null = null;
    const stopAuth = onAuthStateChanged(auth, (user) => {
      stopAdminWatch?.();
      stopAdminWatch = null;
      setError(null);
      if (!user) {
        setViewer(null);
        setStatus("signed-out");
        return;
      }
      setViewer(toViewer(user));
      setStatus("checking");
      // Admin access = a document admins/{uid} exists. Watched live, so removing someone's
      // entry locks them out straight away.
      stopAdminWatch = onSnapshot(
        doc(db, "admins", user.uid),
        (snap) => setStatus(snap.exists() ? "admin" : "denied"),
        (e) => {
          setError(describeError(e));
          setStatus("denied");
        }
      );
    });
    return () => {
      stopAuth();
      stopAdminWatch?.();
    };
  }, [demo]);

  const value = useMemo<AuthState>(
    () => ({
      status,
      viewer,
      error,
      demo,
      async signInWithGoogle() {
        await signInWithPopup(auth, new GoogleAuthProvider());
      },
      async signInWithEmail(email, password) {
        await signInWithEmailAndPassword(auth, email.trim(), password);
      },
      async signOut() {
        if (demo) leaveDemoMode();
        else await firebaseSignOut(auth);
      },
    }),
    [status, viewer, error, demo]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
