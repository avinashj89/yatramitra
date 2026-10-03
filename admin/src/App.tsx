import { useMemo } from "react";
import { BrowserRouter, Route, Routes } from "react-router";
import { Box, CircularProgress, Typography } from "@mui/material";
import { AuthProvider, isDemoMode, useAuth } from "./auth/AuthContext";
import { LoginPage } from "./auth/LoginPage";
import { AccessDenied } from "./auth/AccessDenied";
import { DataProvider } from "./data/DataContext";
import { createDemoSource } from "./data/demoSource";
import { firestoreSource } from "./data/firestoreSource";
import { AdminLayout } from "./layout/AdminLayout";
import { DashboardPage } from "./pages/DashboardPage";
import { TripsPage } from "./pages/TripsPage";
import { TripDetailPage } from "./pages/TripDetailPage";
import { UsersPage } from "./pages/UsersPage";
import { ChatPage } from "./pages/ChatPage";
import { ExpensesPage } from "./pages/ExpensesPage";
import { DevicesPage } from "./pages/DevicesPage";
import { AdminsPage } from "./pages/AdminsPage";
import { EmptyState } from "./components/ui";

function Gate() {
  const { status, demo } = useAuth();
  const source = useMemo(() => (demo ? createDemoSource() : firestoreSource), [demo]);

  if (status === "loading" || status === "checking") {
    return (
      <Box className="flex min-h-full flex-col items-center justify-center gap-3" sx={{ bgcolor: "background.default" }}>
        <CircularProgress />
        <Typography color="text.secondary">{status === "checking" ? "Checking admin access…" : "Loading…"}</Typography>
      </Box>
    );
  }
  if (status === "signed-out") return <LoginPage />;
  if (status === "denied") return <AccessDenied />;

  // Only an admin ever starts the app-wide listeners.
  return (
    <DataProvider source={source}>
      <Routes>
        <Route element={<AdminLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="trips" element={<TripsPage />} />
          <Route path="trips/:code" element={<TripDetailPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="chat" element={<ChatPage />} />
          <Route path="expenses" element={<ExpensesPage />} />
          <Route path="devices" element={<DevicesPage />} />
          <Route path="admins" element={<AdminsPage />} />
          <Route path="*" element={<EmptyState title="Page not found" />} />
        </Route>
      </Routes>
    </DataProvider>
  );
}

export default function App() {
  const demo = useMemo(() => isDemoMode(), []);
  return (
    <BrowserRouter>
      <AuthProvider demo={demo}>
        <Gate />
      </AuthProvider>
    </BrowserRouter>
  );
}
