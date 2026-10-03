import { useState } from "react";
import { Alert, Box, Button, Card, CardContent, Divider, TextField, Typography } from "@mui/material";
import GoogleIcon from "@mui/icons-material/Google";
import PinIcon from "@mui/icons-material/Place";
import { useAuth } from "./AuthContext";

function friendly(error: unknown): string {
  const code = (error as { code?: string })?.code ?? "";
  if (code === "auth/popup-closed-by-user" || code === "auth/cancelled-popup-request") return "";
  if (code === "auth/invalid-credential" || code === "auth/wrong-password" || code === "auth/user-not-found") {
    return "That email and password don't match a YatraMitra account.";
  }
  if (code === "auth/popup-blocked") return "The browser blocked the Google sign-in window. Allow pop-ups for this page and try again.";
  if (code === "auth/unauthorized-domain") {
    return "This address isn't allowed to sign in. In Firebase Console, Authentication, Settings, Authorized domains, add it.";
  }
  if (code === "auth/too-many-requests") return "Too many attempts. Wait a few minutes and try again.";
  return (error as Error)?.message ?? "Sign-in didn't work. Please try again.";
}

export function LoginPage() {
  const { signInWithGoogle, signInWithEmail } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      const message = friendly(e);
      if (message) setError(message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Box className="flex min-h-full items-center justify-center px-4 py-10" sx={{ bgcolor: "background.default" }}>
      <Card className="w-full max-w-md">
        <CardContent className="flex flex-col gap-5 p-8">
          <div className="flex items-center gap-2">
            <PinIcon sx={{ color: "primary.main", fontSize: 32 }} />
            <div>
              <Typography variant="h5" component="h1">
                YatraMitra Admin
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Sign in with an admin account to watch the app live.
              </Typography>
            </div>
          </div>

          <Button
            variant="outlined"
            size="large"
            startIcon={<GoogleIcon />}
            disabled={busy}
            onClick={() => run(signInWithGoogle)}
          >
            Continue with Google
          </Button>

          <Divider>or</Divider>

          <form
            className="flex flex-col gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              run(() => signInWithEmail(email, password));
            }}
          >
            <TextField label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            <TextField
              label="Password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <Button type="submit" variant="contained" size="large" disabled={busy || !email || !password}>
              Sign in
            </Button>
          </form>

          {error && <Alert severity="error">{error}</Alert>}

          <Typography variant="body2" color="text.secondary" className="text-center">
            Just looking?{" "}
            <Button size="small" href="?demo">
              Explore with demo data
            </Button>
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
