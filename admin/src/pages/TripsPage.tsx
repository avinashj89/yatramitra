import { useMemo, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router";
import { Card, Link, ToggleButton, ToggleButtonGroup } from "@mui/material";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import { ErrorBanner, PageHeader, TripStatusChip, tripStage } from "../components/ui";
import { useAdminData } from "../data/DataContext";
import { formatDate, formatDateTime, formatINR } from "../lib/format";
import { countBy } from "../lib/stats";
import type { Trip } from "../types";

type Stage = "All" | "Planning" | "Started" | "Completed";

interface Row extends Trip {
  stage: string;
  members: number;
  organizer: string;
  spent: number;
  days: number;
}

export function TripsPage() {
  const { trips, members, expenses, ready, errors } = useAdminData();
  const [stage, setStage] = useState<Stage>("All");
  const navigate = useNavigate();

  const rows: Row[] = useMemo(() => {
    const memberCounts = countBy(members, (m) => m.tripCode);
    const spent = new Map<string, number>();
    for (const e of expenses) spent.set(e.tripCode, (spent.get(e.tripCode) ?? 0) + e.amount);
    return trips
      .map((t) => ({
        ...t,
        stage: tripStage(t),
        members: memberCounts.get(t.code) ?? 0,
        organizer: members.find((m) => m.tripCode === t.code && m.role === "ORGANIZER")?.name ?? "",
        spent: spent.get(t.code) ?? 0,
        days: t.routePlan?.days.length ?? 0,
      }))
      .filter((r) => stage === "All" || r.stage === stage)
      .sort((a, b) => b.createdAt - a.createdAt);
  }, [trips, members, expenses, stage]);

  const columns: GridColDef<Row>[] = [
    {
      field: "groupName",
      headerName: "Trip",
      flex: 1.4,
      minWidth: 200,
      renderCell: (p) => (
        <Link component={RouterLink} to={`/trips/${p.row.code}`} underline="hover" fontWeight={600}>
          {p.row.groupName}
        </Link>
      ),
    },
    { field: "code", headerName: "Code", width: 100, renderCell: (p) => <code>{p.value}</code> },
    { field: "stage", headerName: "Status", width: 130, renderCell: (p) => <TripStatusChip trip={p.row} /> },
    { field: "organizer", headerName: "Organizer", flex: 1, minWidth: 140 },
    { field: "members", headerName: "People", type: "number", width: 90 },
    { field: "days", headerName: "Route days", type: "number", width: 110 },
    { field: "spent", headerName: "Spent", type: "number", width: 130, valueFormatter: (v: number) => formatINR(v) },
    { field: "createdAt", headerName: "Created", width: 130, valueFormatter: (v: number) => formatDate(v) },
    { field: "startedAt", headerName: "Started", width: 180, valueFormatter: (v: number) => (v > 0 ? formatDateTime(v) : "Not yet") },
  ];

  return (
    <>
      <PageHeader
        title="Trips"
        subtitle={`${trips.length} trips in total. Click a trip to see its people, route, itinerary, expenses and chat.`}
        actions={
          <ToggleButtonGroup size="small" exclusive value={stage} onChange={(_e, v) => v && setStage(v)} aria-label="Filter by status">
            {(["All", "Planning", "Started", "Completed"] as Stage[]).map((s) => (
              <ToggleButton key={s} value={s}>
                {s}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>
        }
      />
      <ErrorBanner errors={[errors.trips, errors.members, errors.expenses]} />
      <Card>
        <DataGrid
          rows={rows}
          columns={columns}
          getRowId={(r) => r.code}
          loading={!ready.trips}
          showToolbar
          slotProps={{ toolbar: { showQuickFilter: true, csvOptions: { fileName: "yatramitra-trips" }, printOptions: { disableToolbarButton: true } } }}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          pageSizeOptions={[10, 25, 50, 100]}
          onRowDoubleClick={(p) => navigate(`/trips/${p.row.code}`)}
          disableRowSelectionOnClick
          autoHeight
        />
      </Card>
    </>
  );
}
