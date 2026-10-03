import { useMemo, useState } from "react";
import { Link as RouterLink } from "react-router";
import { Box, Card, IconButton, InputAdornment, Link, MenuItem, TextField, Tooltip, Typography } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import DeleteIcon from "@mui/icons-material/DeleteOutline";
import { ConfirmDialog, EmptyState, ErrorBanner, PageHeader } from "../components/ui";
import { useActions, useAdminData } from "../data/DataContext";
import { formatDateTime, fromNow } from "../lib/format";
import type { ChatMessage } from "../types";

/** Every trip's Group chat in one live stream, newest first, with removal for moderation. */
export function ChatPage() {
  const { chat, trips, ready, errors } = useAdminData();
  const actions = useActions();
  const [trip, setTrip] = useState("all");
  const [search, setSearch] = useState("");
  const [pending, setPending] = useState<ChatMessage | null>(null);

  const names = useMemo(() => new Map(trips.map((t) => [t.code, t.groupName])), [trips]);
  const tripsWithChat = useMemo(() => [...new Set(chat.map((c) => c.tripCode))], [chat]);
  const shown = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return chat.filter(
      (c) =>
        (trip === "all" || c.tripCode === trip) &&
        (!needle || c.text.toLowerCase().includes(needle) || c.authorName.toLowerCase().includes(needle))
    );
  }, [chat, trip, search]);

  return (
    <>
      <PageHeader title="Group chat" subtitle="Messages from every trip as they're posted (latest 500)." />
      <ErrorBanner errors={[errors.chat]} />
      <Card>
        <div className="flex flex-col gap-3 p-4 sm:flex-row">
          <TextField
            select
            size="small"
            label="Trip"
            value={trip}
            onChange={(e) => setTrip(e.target.value)}
            className="sm:w-72"
          >
            <MenuItem value="all">All trips</MenuItem>
            {tripsWithChat.map((code) => (
              <MenuItem key={code} value={code}>
                {names.get(code) ?? code}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            size="small"
            label="Search messages or names"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="flex-1"
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
          />
        </div>
        <div className="flex flex-col gap-2 px-4 pb-4">
          {!ready.chat ? (
            <Typography color="text.secondary">Loading…</Typography>
          ) : shown.length === 0 ? (
            <EmptyState title={chat.length === 0 ? "No messages yet" : "Nothing matches"} />
          ) : (
            shown.map((m) => (
              <Box key={`${m.tripCode}-${m.id}`} className="flex items-start gap-3 rounded-xl px-4 py-3" sx={{ bgcolor: "action.hover" }}>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-baseline gap-x-2">
                    <Typography variant="subtitle2">{m.authorName}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      in{" "}
                      <Link component={RouterLink} to={`/trips/${m.tripCode}`} underline="hover">
                        {names.get(m.tripCode) ?? m.tripCode}
                      </Link>{" "}
                      · <span title={formatDateTime(m.createdAt)}>{fromNow(m.createdAt)}</span>
                    </Typography>
                  </div>
                  <Typography variant="body2" className="mt-0.5 whitespace-pre-wrap break-words">
                    {m.text}
                  </Typography>
                </div>
                <Tooltip title="Remove message">
                  <IconButton size="small" aria-label="Remove message" onClick={() => setPending(m)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Box>
            ))
          )}
        </div>
      </Card>
      <ConfirmDialog
        open={pending !== null}
        title="Remove this message?"
        message={pending ? `"${pending.text.slice(0, 140)}" by ${pending.authorName} disappears from every phone. This can't be undone.` : ""}
        confirmLabel="Remove"
        danger
        onConfirm={() => (pending ? actions.deleteChatMessage(pending) : Promise.resolve())}
        onClose={() => setPending(null)}
      />
    </>
  );
}
