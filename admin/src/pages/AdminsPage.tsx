import { useMemo, useState } from "react";
import { Alert, Autocomplete, Button, Card, CardContent, IconButton, List, ListItem, ListItemText, TextField, Tooltip, Typography } from "@mui/material";
import DeleteIcon from "@mui/icons-material/PersonRemoveOutlined";
import { useAuth } from "../auth/AuthContext";
import { ConfirmDialog, ErrorBanner, PageHeader, SectionCard } from "../components/ui";
import { useActions, useAdminData } from "../data/DataContext";
import { formatDate } from "../lib/format";
import type { AdminEntry, UserProfile } from "../types";

export function AdminsPage() {
  const { admins, users, errors } = useAdminData();
  const { viewer } = useAuth();
  const actions = useActions();
  const [choice, setChoice] = useState<UserProfile | null>(null);
  const [adding, setAdding] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const [removing, setRemoving] = useState<AdminEntry | null>(null);

  const adminIds = useMemo(() => new Set(admins.map((a) => a.uid)), [admins]);
  const candidates = useMemo(() => users.filter((u) => !adminIds.has(u.uid)), [users, adminIds]);
  const nameOf = (a: AdminEntry) => a.name || users.find((u) => u.uid === a.uid)?.name || a.email || a.uid;

  return (
    <>
      <PageHeader title="Admins" subtitle="Accounts that can open this panel and see everything in the app." />
      <ErrorBanner errors={[errors.admins]} />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <SectionCard title={`Current admins (${admins.length})`}>
          <List disablePadding>
            {admins.map((a) => (
              <ListItem
                key={a.uid}
                disableGutters
                secondaryAction={
                  a.uid === viewer?.uid ? (
                    <Typography variant="caption" color="text.secondary">
                      You
                    </Typography>
                  ) : (
                    <Tooltip title="Remove admin access">
                      <IconButton edge="end" aria-label={`Remove ${nameOf(a)}`} onClick={() => setRemoving(a)}>
                        <DeleteIcon />
                      </IconButton>
                    </Tooltip>
                  )
                }
              >
                <ListItemText primary={nameOf(a)} secondary={[a.email, a.addedAt ? `added ${formatDate(a.addedAt)}` : ""].filter(Boolean).join(" · ")} />
              </ListItem>
            ))}
          </List>
        </SectionCard>

        <Card>
          <CardContent className="flex flex-col gap-4">
            <Typography variant="h6" component="h2">
              Add an admin
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Pick someone who already has a YatraMitra account. They get full read access to every trip, chat and
              expense, and can remove messages and change trip status.
            </Typography>
            <Autocomplete
              options={candidates}
              value={choice}
              onChange={(_e, v) => setChoice(v)}
              getOptionLabel={(u) => [u.name, u.email || u.phone].filter(Boolean).join(" · ")}
              isOptionEqualToValue={(a, b) => a.uid === b.uid}
              renderInput={(params) => <TextField {...params} label="Account" placeholder="Search by name, email or phone" />}
            />
            <div className="flex justify-end">
              <Button
                variant="contained"
                disabled={!choice || adding}
                onClick={async () => {
                  if (!choice) return;
                  setAdding(true);
                  setMessage(null);
                  try {
                    await actions.addAdmin({ uid: choice.uid, email: choice.email, name: choice.name });
                    setMessage({ ok: true, text: `${choice.name} is now an admin.` });
                    setChoice(null);
                  } catch (e) {
                    setMessage({ ok: false, text: (e as Error).message || "Couldn't add that admin." });
                  } finally {
                    setAdding(false);
                  }
                }}
              >
                Make admin
              </Button>
            </div>
            {message && <Alert severity={message.ok ? "success" : "error"}>{message.text}</Alert>}
          </CardContent>
        </Card>
      </div>
      <ConfirmDialog
        open={removing !== null}
        title="Remove admin access?"
        message={removing ? `${nameOf(removing)} will be signed out of the panel straight away.` : ""}
        confirmLabel="Remove"
        danger
        onConfirm={() => (removing ? actions.removeAdmin(removing.uid) : Promise.resolve())}
        onClose={() => setRemoving(null)}
      />
    </>
  );
}
