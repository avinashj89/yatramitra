import { useMemo } from "react";
import { Link as RouterLink } from "react-router";
import { Card, Link } from "@mui/material";
import { DataGrid, type GridColDef } from "@mui/x-data-grid";
import RupeeIcon from "@mui/icons-material/CurrencyRupeeOutlined";
import ReceiptIcon from "@mui/icons-material/ReceiptLongOutlined";
import TrendIcon from "@mui/icons-material/TrendingUp";
import { ErrorBanner, KpiCard, PageHeader } from "../components/ui";
import { useAdminData } from "../data/DataContext";
import { formatDateTime, formatINR, formatINRShort } from "../lib/format";
import type { Expense } from "../types";

interface Row extends Expense {
  trip: string;
  split: string;
}

export function ExpensesPage() {
  const { expenses, trips, ready, errors } = useAdminData();
  const names = useMemo(() => new Map(trips.map((t) => [t.code, t.groupName])), [trips]);

  const rows: Row[] = useMemo(
    () =>
      expenses.map((e) => {
        const custom = Object.keys(e.customSplitAmounts).length;
        return {
          ...e,
          trip: names.get(e.tripCode) ?? e.tripCode,
          split: custom > 0 ? `Custom · ${custom} people` : e.splitAmongMemberIds.length > 0 ? `Equal · ${e.splitAmongMemberIds.length} people` : "Equal · everyone",
        };
      }),
    [expenses, names]
  );

  const total = expenses.reduce((s, e) => s + e.amount, 0);
  const biggest = expenses.reduce<Expense | null>((max, e) => (max && max.amount >= e.amount ? max : e), null);
  const perTrip = new Set(expenses.map((e) => e.tripCode)).size;

  const columns: GridColDef<Row>[] = [
    { field: "createdAt", headerName: "When", width: 180, valueFormatter: (v: number) => formatDateTime(v) },
    {
      field: "trip",
      headerName: "Trip",
      flex: 1,
      minWidth: 170,
      renderCell: (p) => (
        <Link component={RouterLink} to={`/trips/${p.row.tripCode}`} underline="hover">
          {p.row.trip}
        </Link>
      ),
    },
    { field: "description", headerName: "What", flex: 1.2, minWidth: 170 },
    { field: "paidByName", headerName: "Paid by", flex: 0.9, minWidth: 140 },
    { field: "split", headerName: "Split", width: 160 },
    { field: "amount", headerName: "Amount", type: "number", width: 140, valueFormatter: (v: number) => formatINR(v) },
  ];

  return (
    <>
      <PageHeader title="Expenses" subtitle="Every expense logged in every trip (latest 2,000). Export to CSV from the table toolbar." />
      <ErrorBanner errors={[errors.expenses]} />
      <div className="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <KpiCard label="Total logged" value={formatINRShort(total)} hint={`${expenses.length} expenses`} icon={<RupeeIcon />} loading={!ready.expenses} />
        <KpiCard label="Trips with expenses" value={perTrip} hint={perTrip ? `${formatINRShort(total / perTrip)} per trip on average` : undefined} icon={<ReceiptIcon />} loading={!ready.expenses} />
        <KpiCard
          label="Biggest single expense"
          value={biggest ? formatINRShort(biggest.amount) : "—"}
          hint={biggest ? `${biggest.description || "Untitled"} · ${names.get(biggest.tripCode) ?? biggest.tripCode}` : undefined}
          icon={<TrendIcon />}
          loading={!ready.expenses}
        />
      </div>
      <Card>
        <DataGrid
          rows={rows}
          columns={columns}
          getRowId={(r) => `${r.tripCode}/${r.id}`}
          loading={!ready.expenses}
          showToolbar
          slotProps={{ toolbar: { showQuickFilter: true, csvOptions: { fileName: "yatramitra-expenses" }, printOptions: { disableToolbarButton: true } } }}
          initialState={{ pagination: { paginationModel: { pageSize: 25 } }, sorting: { sortModel: [{ field: "createdAt", sort: "desc" }] } }}
          pageSizeOptions={[10, 25, 50, 100]}
          disableRowSelectionOnClick
          autoHeight
        />
      </Card>
    </>
  );
}
