import { Alert, Box, Button, Card, CardContent, Typography } from "@mui/material";
import { CopyButton } from "../components/ui";
import { useAuth } from "./AuthContext";

/** Signed in, but not (yet) an admin: explains exactly how to grant access. */
export function AccessDenied() {
  const { viewer, error, signOut } = useAuth();
  if (!viewer) return null;
  return (
    <Box className="flex min-h-full items-center justify-center px-4 py-10" sx={{ bgcolor: "background.default" }}>
      <Card className="w-full max-w-xl">
        <CardContent className="flex flex-col gap-4 p-8">
          <Typography variant="h5" component="h1">
            This account isn't an admin yet
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Signed in as <strong>{viewer.email || viewer.name}</strong>. Only accounts listed in Firestore's
            <code className="mx-1 rounded px-1 py-0.5" style={{ background: "var(--mui-palette-action-hover)" }}>admins</code>
            collection can open the panel, so the app's data stays private.
          </Typography>

          <div>
            <Typography variant="subtitle2" gutterBottom>
              Your account ID (UID)
            </Typography>
            <div className="flex items-center gap-1 rounded-lg px-3 py-2" style={{ background: "var(--mui-palette-action-hover)" }}>
              <code className="flex-1 break-all text-sm">{viewer.uid}</code>
              <CopyButton value={viewer.uid} label="Copy UID" />
            </div>
          </div>

          <div>
            <Typography variant="subtitle2" gutterBottom>
              To make this account the first admin (one time, about a minute)
            </Typography>
            <ol className="ml-5 list-decimal space-y-1 text-sm">
              <li>Open Firebase Console → your project → Firestore Database → Data.</li>
              <li>Click "Start collection", name it <strong>admins</strong>.</li>
              <li>Document ID: paste the UID above.</li>
              <li>Add a field <strong>email</strong> (string) with your email, then Save.</li>
            </ol>
            <Typography variant="body2" color="text.secondary" className="mt-2">
              This page opens by itself as soon as the document exists. After that, admins can add other admins from
              the panel's Admins page.
            </Typography>
          </div>

          {error && <Alert severity="warning">{error}</Alert>}

          <div className="flex justify-end">
            <Button onClick={signOut}>Sign out</Button>
          </div>
        </CardContent>
      </Card>
    </Box>
  );
}
