import { useMemo } from "react";
import { Link as RouterLink, useSearchParams } from "react-router";
import { Avatar, Box, Card, Chip, CircularProgress, Divider, Drawer, IconButton, Link, List, ListItem, ListItemText, Typography } from "@mui/material";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import CloseIcon from "@mui/icons-material/Close";
import { CopyButton, EmptyState, ErrorBanner, PageHeader } from "../components/ui";
import { useAdminData, useUserTrips } from "../data/DataContext";
import { formatDateTime, fromNow, maskToken } from "../lib/format";
import { countBy, tripsByAccount } from "../lib/stats";
import type { UserProfile } from "../types";

interface Row extends UserProfile {
  signIn: string;
  trips: number;
  devices: number;
  lastSeen: number;
  admin: boolean;
}

export function UsersPage() {
  const { users, members, devices, admins, ready, errors } = useAdminData();
  const [params, setParams] = useSearchParams();
  const selectedUid = params.get("uid");

  const rows: Row[] = useMemo(() => {
    const tripCounts = tripsByAccount(members);
    const deviceCounts = countBy(devices, (d) => d.uid);
    const lastSeen = new Map<string, number>();
    for (const d of devices) lastSeen.set(d.uid, Math.max(lastSeen.get(d.uid) ?? 0, d.updatedAt));
    const adminIds = new Set(admins.map((a) => a.uid));
    return users
      .map((u) => ({
        ...u,
        signIn: u.phone && !u.email ? "Phone" : u.email ? "Email / Google" : "—",
        trips: tripCounts.get(u.uid)?.length ?? 0,
        devices: deviceCounts.get(u.uid) ?? 0,
        lastSeen: lastSeen.get(u.uid) ?? 0,
        admin: adminIds.has(u.uid),
      }))
      .sort((a, b) => b.lastSeen - a.lastSeen || a.name.localeCompare(b.name));
  }, [users, members, devices, admins]);

  const columns: GridColDef<Row>[] = [
    {
      field: "name",
      headerName: "Name",
      flex: 1.2,
      minWidth: 180,
      renderCell: (p) => (
        <span className="flex items-center gap-2">
          <Link component="button" underline="hover" fontWeight={600} onClick={() => setParams({ uid: p.row.uid })}>
            {p.row.name}
          </Link>
          {p.row.admin && <Chip size="small" label="Admin" color="primary" variant="outlined" />}
        </span>
      ),
    },
    { field: "email", headerName: "Email", flex: 1.2, minWidth: 180 },
    { field: "phone", headerName: "Phone", width: 150 },
    { field: "signIn", headerName: "Signs in with", width: 140 },
    { field: "trips", headerName: "Trips", type: "number", width: 80 },
    { field: "devices", headerName: "Phones", type: "number", width: 90 },
    { field: "lastSeen", headerName: "Last app sign-in", width: 160, valueFormatter: (v: number) => (v > 0 ? fromNow(v) : "—") },
  ];

  return (
    <>
      <PageHeader title="Users" subtitle={`${users.length} accounts. "Last app sign-in" is when that phone last registered for notifications.`} />
      <ErrorBanner errors={[errors.users, errors.members, errors.devices]} />
      <Card>
        <DataGrid
          rows={rows}
          columns={columns}
          getRowId={(r) => r.uid}
          loading={!ready.users}
          showToolbar
          slotProps={{ toolbar: { showQuickFilter: true, csvOptions: { fileName: "yatramitra-users" }, printOptions: { disableToolbarButton: true } } }}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[10, 25, 50, 100]}
          onRowClick={(p) => setParams({ uid: p.row.uid })}
          disableRowSelectionOnClick
          autoHeight
        />
      </Card>
      <UserDrawer uid={selectedUid} onClose={() => setParams({})} />
    </>
  );
}

function UserDrawer({ uid, onClose }: { uid: string | null; onClose: () => void }) {
  const { users, members, devices, trips } = useAdminData();
  const userTrips = useUserTrips(uid);
  const user = users.find((u) => u.uid === uid);
  const memberships = members.filter((m) => m.uid === uid);
  const phones = devices.filter((d) => d.uid === uid);
  const tripName = (code: string) => trips.find((t) => t.code === code)?.groupName ?? code;

  return (
    <Drawer anchor="right" open={Boolean(uid)} onClose={onClose} slotProps={{ paper: { sx: { width: { xs: "100%", sm: 440 } } } }}>
      <Box className="flex items-center justify-between px-5 py-4">
        <Typography variant="h6">Account</Typography>
        <IconButton onClick={onClose} aria-label="Close">
          <CloseIcon />
        </IconButton>
      </Box>
      <Divider />
      {!user ? (
        <EmptyState title="Account not found" />
      ) : (
        <div className="flex flex-col gap-5 p-5">
          <div className="flex items-center gap-3">
            <Avatar sx={{ bgcolor: "primary.main", width: 48, height: 48 }}>{user.name.slice(0, 1).toUpperCase()}</Avatar>
            <div className="min-w-0">
              <Typography variant="subtitle1" fontWeight={700}>
                {user.name}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {[user.email, user.phone].filter(Boolean).join(" · ") || "No contact details"}
              </Typography>
            </div>
          </div>
          <div className="flex items-center gap-1 rounded-lg px-3 py-2" style={{ background: "var(--mui-palette-action-hover)" }}>
            <Typography variant="caption" className="flex-1 break-all">
              UID {user.uid}
            </Typography>
            <CopyButton value={user.uid} label="Copy UID" />
          </div>

          <div>
            <Typography variant="subtitle2" gutterBottom>
              Trips ({memberships.length})
            </Typography>
            {memberships.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                Not on any trip.
              </Typography>
            ) : (
              <List dense disablePadding>
                {memberships.map((m) => (
                  <ListItem key={`${m.tripCode}-${m.id}`} disableGutters>
                    <ListItemText
                      primary={
                        <Link component={RouterLink} to={`/trips/${m.tripCode}`} underline="hover">
                          {tripName(m.tripCode)}
                        </Link>
                      }
                      secondary={`${m.role === "ORGANIZER" ? "Organizer" : "Member"} · joined ${formatDateTime(m.joinedAt)}`}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </div>

          <div>
            <Typography variant="subtitle2" gutterBottom>
              Their homepage list
            </Typography>
            {!userTrips.ready ? (
              <CircularProgress size={20} />
            ) : userTrips.error ? (
              <Typography variant="body2" color="error">
                {userTrips.error}
              </Typography>
            ) : userTrips.trips.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                Empty.
              </Typography>
            ) : (
              <List dense disablePadding>
                {userTrips.trips.map((t) => (
                  <ListItem key={t.tripCode} disableGutters>
                    <ListItemText primary={t.tripName || t.tripCode} secondary={`last opened ${fromNow(t.lastAccessedAt)}`} />
                  </ListItem>
                ))}
              </List>
            )}
            {userTrips.ready && memberships.length > userTrips.trips.length && (
              <Typography variant="caption" color="text.secondary">
                They're on {memberships.length - userTrips.trips.length} trip(s) that aren't in their list yet; the app adds them when the trip is next opened.
              </Typography>
            )}
          </div>

          <div>
            <Typography variant="subtitle2" gutterBottom>
              Phones getting notifications ({phones.length})
            </Typography>
            {phones.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                None. They won't get push notifications until they open the latest app version.
              </Typography>
            ) : (
              <List dense disablePadding>
                {phones.map((d) => (
                  <ListItem key={d.token} disableGutters>
                    <ListItemText primary={`${d.platform} · ${maskToken(d.token)}`} secondary={`registered ${fromNow(d.updatedAt)}`} />
                  </ListItem>
                ))}
              </List>
            )}
          </div>
        </div>
      )}
    </Drawer>
  );
}
