import { useMemo, type ReactNode } from "react";
import { Link as RouterLink } from "react-router";
import { Avatar, Link, List, ListItem, ListItemAvatar, ListItemText, Typography } from "@mui/material";
import { BarChart } from "@mui/x-charts/BarChart";
import { PieChart } from "@mui/x-charts/PieChart";
import PeopleIcon from "@mui/icons-material/PeopleAltOutlined";
import TripIcon from "@mui/icons-material/LuggageOutlined";
import GroupIcon from "@mui/icons-material/Groups2Outlined";
import RupeeIcon from "@mui/icons-material/CurrencyRupeeOutlined";
import ChatIcon from "@mui/icons-material/ForumOutlined";
import BellIcon from "@mui/icons-material/NotificationsActiveOutlined";
import FlagIcon from "@mui/icons-material/Flag";
import AddIcon from "@mui/icons-material/AddLocationAlt";
import PersonAddIcon from "@mui/icons-material/PersonAddAlt1";
import { EmptyState, ErrorBanner, KpiCard, PageHeader, SectionCard } from "../components/ui";
import { useAdminData } from "../data/DataContext";
import { formatINRShort, fromNow } from "../lib/format";
import { activityFeed, computeKpis, dailySeries } from "../lib/stats";
import type { ActivityEvent } from "../types";

// Readable on both the light and the dark background; the stage colours match the status chips.
const CHART = { orange: "#F97316", teal: "#14B8A6", blue: "#3B82F6", green: "#22C55E", grey: "#94A3B8" };

const ICONS: Record<ActivityEvent["kind"], ReactNode> = {
  "trip-created": <AddIcon fontSize="small" />,
  "trip-started": <FlagIcon fontSize="small" />,
  "member-joined": <PersonAddIcon fontSize="small" />,
  expense: <RupeeIcon fontSize="small" />,
  chat: <ChatIcon fontSize="small" />,
};

export function DashboardPage() {
  const data = useAdminData();
  const { trips, users, members, expenses, chat, devices, ready, errors } = data;
  const now = useMemo(() => Date.now(), [trips, members, expenses, chat]);

  const kpis = useMemo(() => computeKpis({ trips, users, members, expenses, chat, devices }, now), [trips, users, members, expenses, chat, devices, now]);
  const newTrips = useMemo(() => dailySeries(trips.map((t) => t.createdAt), 30, now), [trips, now]);
  const newMembers = useMemo(() => dailySeries(members.map((m) => m.joinedAt), 30, now), [members, now]);
  const spend = useMemo(() => dailySeries(expenses.map((e) => e.createdAt), 30, now, expenses.map((e) => e.amount)), [expenses, now]);
  const feed = useMemo(() => activityFeed({ trips, members, expenses, chat }, 25), [trips, members, expenses, chat]);

  const loading = (key: keyof typeof ready) => !ready[key];
  const statusSlices = [
    { id: 0, value: kpis.ongoing - kpis.started, label: "Planning", color: CHART.blue },
    { id: 1, value: kpis.started, label: "Started", color: CHART.green },
    { id: 2, value: kpis.completed, label: "Completed", color: CHART.grey },
  ].filter((s) => s.value > 0);

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Everything happening in YatraMitra, updating live." />
      <ErrorBanner errors={Object.values(errors)} />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <KpiCard label="Users" value={kpis.users} hint="Accounts that have signed in" icon={<PeopleIcon />} loading={loading("users")} />
        <KpiCard
          label="Trips"
          value={kpis.trips}
          hint={`${kpis.ongoing - kpis.started} planning · ${kpis.started} started · ${kpis.completed} completed`}
          icon={<TripIcon />}
          loading={loading("trips")}
        />
        <KpiCard
          label="People on trips"
          value={kpis.members}
          hint={`${kpis.contactOnlyMembers} added as contacts without the app`}
          icon={<GroupIcon />}
          loading={loading("members")}
        />
        <KpiCard label="Expenses logged" value={formatINRShort(kpis.expenseTotal)} hint={`${kpis.expenseCount} expenses`} icon={<RupeeIcon />} loading={loading("expenses")} />
        <KpiCard label="Chat messages" value={kpis.chatLast7Days} hint="Sent in the last 7 days" icon={<ChatIcon />} loading={loading("chat")} />
        <KpiCard
          label="Phones getting notifications"
          value={kpis.devices}
          hint={`${kpis.usersWithNotifications} of ${kpis.users} users`}
          icon={<BellIcon />}
          loading={loading("devices")}
        />
      </div>

      <div className="mt-6 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <SectionCard title="New trips and people (last 30 days)" className="xl:col-span-2">
          <BarChart
            height={280}
            xAxis={[{ scaleType: "band", data: newTrips.map((p) => p.day), tickLabelInterval: (_v, i) => i % 5 === 0 }]}
            yAxis={[{ tickMinStep: 1 }]}
            series={[
              { data: newTrips.map((p) => p.value), label: "Trips created", color: CHART.orange },
              { data: newMembers.map((p) => p.value), label: "People joined", color: CHART.teal },
            ]}
            margin={{ left: 0, right: 10 }}
          />
        </SectionCard>
        <SectionCard title="Trip status">
          {statusSlices.length === 0 ? (
            <EmptyState title="No trips yet" />
          ) : (
            <PieChart height={280} series={[{ data: statusSlices, innerRadius: 60, paddingAngle: 2, cornerRadius: 4 }]} />
          )}
        </SectionCard>
      </div>

      <div className="mt-6 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <SectionCard title="Spending logged per day (₹, last 30 days)" className="xl:col-span-2">
          <BarChart
            height={260}
            xAxis={[{ scaleType: "band", data: spend.map((p) => p.day), tickLabelInterval: (_v, i) => i % 5 === 0 }]}
            yAxis={[{ width: 52, valueFormatter: (v: number) => (v >= 1000 ? `${v / 1000}k` : `${v}`) }]}
            series={[{ data: spend.map((p) => Math.round(p.value)), label: "₹ logged", color: CHART.orange, valueFormatter: (v) => (v == null ? "" : formatINRShort(v)) }]}
            margin={{ left: 0, right: 10 }}
          />
        </SectionCard>
        <SectionCard title="Live activity">
          {feed.length === 0 ? (
            <EmptyState title="Nothing yet">New trips, people joining, expenses and chat messages show up here as they happen.</EmptyState>
          ) : (
            <List dense disablePadding className="max-h-[420px] overflow-y-auto">
              {feed.map((event) => (
                <ListItem key={event.id} disableGutters alignItems="flex-start">
                  <ListItemAvatar sx={{ minWidth: 44 }}>
                    <Avatar sx={{ width: 32, height: 32, bgcolor: "action.hover", color: "primary.main" }}>{ICONS[event.kind]}</Avatar>
                  </ListItemAvatar>
                  <ListItemText
                    primary={<span className="line-clamp-2">{event.text}</span>}
                    secondary={
                      <>
                        <Link component={RouterLink} to={`/trips/${event.tripCode}`} underline="hover">
                          {event.tripName}
                        </Link>
                        {" · "}
                        {fromNow(event.at)}
                      </>
                    }
                  />
                </ListItem>
              ))}
            </List>
          )}
          <Typography variant="caption" color="text.secondary" component="p" className="mt-2">
            Shows the latest 25 events.
          </Typography>
        </SectionCard>
      </div>
    </>
  );
}
