import { useMemo, useState } from "react";
import { Link as RouterLink, useParams } from "react-router";
import {
  Alert,
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  Tooltip,
  Typography,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import DeleteIcon from "@mui/icons-material/DeleteOutline";
import LoopIcon from "@mui/icons-material/Loop";
import { ConfirmDialog, CopyButton, EmptyState, ErrorBanner, PageHeader, SectionCard, TripStatusChip } from "../components/ui";
import { ChatMessageBox } from "../components/ChatMessageBox";
import { useActions, useAdminData, useTripDetail } from "../data/DataContext";
import { computeBalances, computeSettlements } from "../lib/balances";
import { formatDate, formatDateTime, formatINR } from "../lib/format";
import type { ChatMessage, Expense, Member, Trip } from "../types";

export function TripDetailPage() {
  const { code = "" } = useParams();
  const data = useAdminData();
  const actions = useActions();
  const detail = useTripDetail(code);
  const [tab, setTab] = useState(0);
  const [confirm, setConfirm] = useState<null | "complete" | "reopen">(null);

  const trip = data.trips.find((t) => t.code === code);
  const members = useMemo(() => data.members.filter((m) => m.tripCode === code).sort((a, b) => a.joinedAt - b.joinedAt), [data.members, code]);
  const expenses = useMemo(() => data.expenses.filter((e) => e.tripCode === code).sort((a, b) => b.createdAt - a.createdAt), [data.expenses, code]);

  if (!data.ready.trips) {
    return (
      <div className="flex justify-center py-20">
        <CircularProgress />
      </div>
    );
  }
  if (!trip) {
    return (
      <>
        <Button component={RouterLink} to="/trips" startIcon={<ArrowBackIcon />} className="mb-4">
          All trips
        </Button>
        <EmptyState title={`No trip with the code ${code}`}>It may have been deleted.</EmptyState>
      </>
    );
  }

  const organizer = members.find((m) => m.role === "ORGANIZER");

  return (
    <>
      <Button component={RouterLink} to="/trips" startIcon={<ArrowBackIcon />} className="mb-3">
        All trips
      </Button>
      <PageHeader
        title={trip.groupName}
        subtitle={
          <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <TripStatusChip trip={trip} />
            <span className="inline-flex items-center">
              Code <code className="mx-1 font-semibold">{trip.code}</code>
              <CopyButton value={trip.code} label="Copy trip code" />
            </span>
            <span>Created {formatDate(trip.createdAt)}</span>
            {trip.startedAt > 0 && <span>Started {formatDateTime(trip.startedAt)} by {trip.startedBy || "the Organizer"}</span>}
            {organizer && <span>Organizer: {organizer.name}</span>}
          </span>
        }
        actions={
          trip.status === "COMPLETED" ? (
            <Button variant="outlined" onClick={() => setConfirm("reopen")}>
              Reopen trip
            </Button>
          ) : (
            <Button variant="outlined" onClick={() => setConfirm("complete")}>
              Mark completed
            </Button>
          )
        }
      />
      <ErrorBanner errors={[detail.error]} />

      <Card>
        <Tabs value={tab} onChange={(_e, v) => setTab(v)} variant="scrollable" allowScrollButtonsMobile sx={{ px: 2, borderBottom: 1, borderColor: "divider" }}>
          <Tab label="Overview" />
          <Tab label={`People (${members.length})`} />
          <Tab label={`Expenses (${expenses.length})`} />
          <Tab label={`Group chat (${detail.chat.length})`} />
        </Tabs>
        <Box className="p-4 sm:p-6">
          {tab === 0 && <Overview trip={trip} detail={detail} />}
          {tab === 1 && <People members={members} />}
          {tab === 2 && <Expenses members={members} expenses={expenses} />}
          {tab === 3 && <Chat messages={detail.chat} ready={detail.ready} onDelete={actions.deleteChatMessage} />}
        </Box>
      </Card>

      <ConfirmDialog
        open={confirm !== null}
        title={confirm === "reopen" ? "Reopen this trip?" : "Mark this trip completed?"}
        message={
          confirm === "reopen"
            ? "The Organizer and members can edit it again, and everyone on the trip gets a notification."
            : "The trip becomes read-only on every phone (the Organizer can reopen it), and everyone on the trip gets a notification."
        }
        confirmLabel={confirm === "reopen" ? "Reopen" : "Mark completed"}
        onConfirm={() => actions.setTripStatus(trip.code, confirm === "reopen" ? "ONGOING" : "COMPLETED")}
        onClose={() => setConfirm(null)}
      />
    </>
  );
}

function Overview({ trip, detail }: { trip: Trip; detail: ReturnType<typeof useTripDetail> }) {
  const plan = trip.routePlan;
  return (
    <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <SectionCard title="Route, day by day">
        {!plan || plan.days.every((d) => !d.from && d.toStops.every((s) => !s)) ? (
          <EmptyState title="No route planned yet" />
        ) : (
          <div className="flex flex-col gap-3">
            {plan.days.map((day, i) => (
              <div key={i}>
                <Typography variant="subtitle2" className="flex items-center gap-1">
                  Day {i + 1}
                  {day.roundTrip && (
                    <Tooltip title="Round trip: ends back at the start">
                      <LoopIcon fontSize="inherit" color="secondary" />
                    </Tooltip>
                  )}
                  {!day.pitstopsEnabled && <Chip size="small" label="No pitstops" variant="outlined" className="ml-1" />}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {[day.from || "Start not set", ...day.toStops.filter(Boolean), ...(day.roundTrip && day.from ? [day.from] : [])].join("  →  ")}
                </Typography>
              </div>
            ))}
            <Divider />
            <Typography variant="body2" color="text.secondary">
              {plan.days.some((day) => day.pitstopsEnabled)
                ? `Pitstops every ${plan.breakEvery || "?"} ${plan.breakUnit === "KM" ? "km" : "hours"}${
                    plan.pitstopCategories.length > 0 ? ` · ${plan.pitstopCategories.join(", ")}` : ""
                  }`
                : "Pitstops off on every day"}
            </Typography>
          </div>
        )}
      </SectionCard>
      <SectionCard title="Itinerary">
        {!detail.ready ? (
          <CircularProgress size={24} />
        ) : detail.itinerary.length === 0 ? (
          <EmptyState title="No itinerary days yet" />
        ) : (
          <div className="flex flex-col gap-4">
            {detail.itinerary.map((day) => (
              <div key={day.id}>
                <Typography variant="subtitle2">{day.label}</Typography>
                {day.stops.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    No stops
                  </Typography>
                ) : (
                  <List dense disablePadding>
                    {day.stops.map((s) => (
                      <ListItem key={s.id || s.order} disableGutters>
                        <ListItemText
                          primary={
                            <span>
                              <span className="inline-block min-w-[150px] text-sm opacity-70">{[s.fromTime, s.tillTime].filter(Boolean).join(" – ") || "Any time"}</span>
                              {s.place || "Untitled stop"}
                            </span>
                          }
                          secondary={[s.notes, s.source !== "MANUAL" ? "from the route" : ""].filter(Boolean).join(" · ") || undefined}
                        />
                      </ListItem>
                    ))}
                  </List>
                )}
              </div>
            ))}
          </div>
        )}
      </SectionCard>
    </div>
  );
}

function People({ members }: { members: Member[] }) {
  if (members.length === 0) return <EmptyState title="Nobody on this trip yet" />;
  return (
    <div className="overflow-x-auto">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>Role</TableCell>
            <TableCell>Account</TableCell>
            <TableCell>Phone</TableCell>
            <TableCell>Email</TableCell>
            <TableCell>UPI</TableCell>
            <TableCell>Joined</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {members.map((m) => (
            <TableRow key={m.id}>
              <TableCell sx={{ fontWeight: 600 }}>{m.name}</TableCell>
              <TableCell>
                <Chip size="small" label={m.role === "ORGANIZER" ? "Organizer" : "Member"} color={m.role === "ORGANIZER" ? "primary" : "default"} variant="outlined" />
              </TableCell>
              <TableCell>
                {m.uid ? (
                  <RouterLinkText to={`/users?uid=${m.uid}`}>Has the app</RouterLinkText>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    Contact only
                  </Typography>
                )}
              </TableCell>
              <TableCell>{m.phone || "—"}</TableCell>
              <TableCell>{m.email || "—"}</TableCell>
              <TableCell>{m.upiId || "—"}</TableCell>
              <TableCell>{formatDate(m.joinedAt)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

function RouterLinkText({ to, children }: { to: string; children: string }) {
  return (
    <Typography component={RouterLink} to={to} variant="body2" color="primary" sx={{ textDecoration: "none", "&:hover": { textDecoration: "underline" } }}>
      {children}
    </Typography>
  );
}

function Expenses({ members, expenses }: { members: Member[]; expenses: Expense[] }) {
  const balances = useMemo(() => computeBalances(members, expenses), [members, expenses]);
  const settlements = useMemo(() => computeSettlements(balances), [balances]);
  const total = expenses.reduce((s, e) => s + e.amount, 0);
  const names = new Map(members.map((m) => [m.id, m.name]));
  if (expenses.length === 0) return <EmptyState title="No expenses yet" />;
  return (
    <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
      <div className="overflow-x-auto xl:col-span-2">
        <Typography variant="subtitle2" className="mb-2">
          {expenses.length} expenses · {formatINR(total)} in total
        </Typography>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>When</TableCell>
              <TableCell>What</TableCell>
              <TableCell>Paid by</TableCell>
              <TableCell>Split</TableCell>
              <TableCell align="right">Amount</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {expenses.map((e) => {
              const custom = Object.keys(e.customSplitAmounts).length;
              const split = custom > 0 ? `Custom (${custom} people)` : e.splitAmongMemberIds.length > 0 ? e.splitAmongMemberIds.map((id) => names.get(id) ?? "?").join(", ") : "Everyone equally";
              return (
                <TableRow key={e.id}>
                  <TableCell sx={{ whiteSpace: "nowrap" }}>{formatDateTime(e.createdAt)}</TableCell>
                  <TableCell>{e.description || "—"}</TableCell>
                  <TableCell>{e.paidByName || names.get(e.paidByMemberId) || "—"}</TableCell>
                  <TableCell sx={{ maxWidth: 220 }}>{split}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: 600 }}>
                    {formatINR(e.amount)}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
      <div className="flex flex-col gap-4">
        <SectionCard title="Balances">
          <List dense disablePadding>
            {balances.map((b) => (
              <ListItem key={b.memberId} disableGutters secondaryAction={
                <Typography variant="body2" fontWeight={600} color={b.net > 0.01 ? "success.main" : b.net < -0.01 ? "error.main" : "text.secondary"}>
                  {b.net > 0.01 ? "gets " : b.net < -0.01 ? "owes " : ""}
                  {formatINR(Math.abs(b.net))}
                </Typography>
              }>
                <ListItemText primary={b.name} />
              </ListItem>
            ))}
          </List>
        </SectionCard>
        <SectionCard title="Settle up">
          {settlements.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              Everyone is settled.
            </Typography>
          ) : (
            <List dense disablePadding>
              {settlements.map((s, i) => (
                <ListItem key={i} disableGutters>
                  <ListItemText primary={`${s.fromName} pays ${s.toName}`} secondary={formatINR(s.amount)} />
                </ListItem>
              ))}
            </List>
          )}
        </SectionCard>
      </div>
    </div>
  );
}

function Chat({ messages, ready, onDelete }: { messages: ChatMessage[]; ready: boolean; onDelete: (m: ChatMessage) => Promise<void> }) {
  const [pending, setPending] = useState<ChatMessage | null>(null);
  if (!ready) return <CircularProgress size={24} />;
  if (messages.length === 0) return <EmptyState title="No messages yet" />;
  return (
    <>
      <Alert severity="info" variant="outlined" className="mb-4">
        Messages from the trip's Group chat, oldest first. "Older suggestion" marks posts made before the chat replaced the
        suggestion boxes.
      </Alert>
      <div className="flex max-h-[600px] flex-col gap-2 overflow-y-auto pr-1">
        {messages.map((m) => (
          <ChatMessageBox
            key={`${m.source}-${m.id}`}
            message={m}
            meta={
              <>
                {formatDateTime(m.createdAt)}
                {m.source !== "chat" && <Chip size="small" label="Older suggestion" variant="outlined" className="ml-2" />}
              </>
            }
            action={
              <Tooltip title="Remove message">
                <IconButton size="small" aria-label="Remove message" onClick={() => setPending(m)} sx={{ color: m.sos ? "#fff" : undefined }}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            }
          />
        ))}
      </div>
      <ConfirmDialog
        open={pending !== null}
        title="Remove this message?"
        message={pending ? `"${pending.text.slice(0, 140)}" by ${pending.authorName} disappears from every phone. This can't be undone.` : ""}
        confirmLabel="Remove"
        danger
        onConfirm={() => (pending ? onDelete(pending) : Promise.resolve())}
        onClose={() => setPending(null)}
      />
    </>
  );
}
