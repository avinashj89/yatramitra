import { useMemo } from "react";
import { Alert, Card, Link } from "@mui/material";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import { Link as RouterLink } from "react-router";
import PhoneIcon from "@mui/icons-material/PhoneAndroidOutlined";
import PeopleIcon from "@mui/icons-material/PeopleAltOutlined";
import QuietIcon from "@mui/icons-material/NotificationsOffOutlined";
import { ErrorBanner, KpiCard, PageHeader } from "../components/ui";
import { useAdminData } from "../data/DataContext";
import { formatDateTime, fromNow, maskToken } from "../lib/format";
import type { DeviceToken } from "../types";

interface Row extends DeviceToken {
  name: string;
  contact: string;
}

export function DevicesPage() {
  const { devices, users, ready, errors } = useAdminData();
  const byUid = useMemo(() => new Map(users.map((u) => [u.uid, u])), [users]);
  const rows: Row[] = useMemo(
    () =>
      devices
        .map((d) => {
          const u = byUid.get(d.uid);
          return { ...d, name: u?.name ?? "Unknown account", contact: [u?.email, u?.phone].filter(Boolean).join(" · ") };
        })
        .sort((a, b) => b.updatedAt - a.updatedAt),
    [devices, byUid]
  );
  const reachable = new Set(devices.map((d) => d.uid));
  const withoutPhones = users.filter((u) => !reachable.has(u.uid)).length;

  const columns: GridColDef<Row>[] = [
    {
      field: "name",
      headerName: "Account",
      flex: 1,
      minWidth: 170,
      renderCell: (p) => (
        <Link component={RouterLink} to={`/users?uid=${p.row.uid}`} underline="hover" fontWeight={600}>
          {p.row.name}
        </Link>
      ),
    },
    { field: "contact", headerName: "Email / phone", flex: 1.2, minWidth: 190 },
    { field: "platform", headerName: "Platform", width: 110 },
    { field: "updatedAt", headerName: "Registered", width: 190, valueFormatter: (v: number) => `${fromNow(v)} (${formatDateTime(v)})` },
    { field: "token", headerName: "Push address", width: 190, valueFormatter: (v: string) => maskToken(v), sortable: false },
  ];

  return (
    <>
      <PageHeader title="Devices & notifications" subtitle="Phones that receive push notifications, and for which account." />
      <ErrorBanner errors={[errors.devices, errors.users]} />
      <div className="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <KpiCard label="Phones registered" value={devices.length} icon={<PhoneIcon />} loading={!ready.devices} />
        <KpiCard label="Accounts reachable" value={reachable.size} hint={`of ${users.length} accounts`} icon={<PeopleIcon />} loading={!ready.devices} />
        <KpiCard label="Accounts with no phone" value={withoutPhones} hint="They haven't opened the latest app since signing in" icon={<QuietIcon />} loading={!ready.devices || !ready.users} />
      </div>
      <Alert severity="info" variant="outlined" className="mb-4">
        The app registers a phone here when someone signs in and removes it when they sign out. The notification server
        (Firebase Cloud Functions) sends to these phones for chat messages, trip started / completed / reopened / renamed,
        people joining and new expenses. Dead entries are cleaned up automatically after a failed send.
      </Alert>
      <Card>
        <DataGrid
          rows={rows}
          columns={columns}
          getRowId={(r) => r.token}
          loading={!ready.devices}
          showToolbar
          slotProps={{ toolbar: { showQuickFilter: true, csvOptions: { disableToolbarButton: true }, printOptions: { disableToolbarButton: true } } }}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[10, 25, 50]}
          disableRowSelectionOnClick
          autoHeight
        />
      </Card>
    </>
  );
}
