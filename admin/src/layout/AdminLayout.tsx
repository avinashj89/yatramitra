import { useState, type ReactNode } from "react";
import { NavLink, Outlet, useLocation } from "react-router";
import {
  Alert,
  AppBar,
  Avatar,
  Box,
  Button,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { useColorScheme } from "@mui/material/styles";
import MenuIcon from "@mui/icons-material/Menu";
import DashboardIcon from "@mui/icons-material/SpaceDashboardOutlined";
import TripIcon from "@mui/icons-material/LuggageOutlined";
import PeopleIcon from "@mui/icons-material/PeopleAltOutlined";
import ChatIcon from "@mui/icons-material/ForumOutlined";
import ExpenseIcon from "@mui/icons-material/CurrencyRupeeOutlined";
import DevicesIcon from "@mui/icons-material/NotificationsActiveOutlined";
import AdminIcon from "@mui/icons-material/AdminPanelSettingsOutlined";
import DarkIcon from "@mui/icons-material/DarkModeOutlined";
import LightIcon from "@mui/icons-material/LightModeOutlined";
import PinIcon from "@mui/icons-material/Place";
import { leaveDemoMode, useAuth } from "../auth/AuthContext";
import { useAdminData, useDataMode } from "../data/DataContext";

const DRAWER_WIDTH = 248;

const NAV: { to: string; label: string; icon: ReactNode }[] = [
  { to: "/", label: "Dashboard", icon: <DashboardIcon /> },
  { to: "/trips", label: "Trips", icon: <TripIcon /> },
  { to: "/users", label: "Users", icon: <PeopleIcon /> },
  { to: "/chat", label: "Group chat", icon: <ChatIcon /> },
  { to: "/expenses", label: "Expenses", icon: <ExpenseIcon /> },
  { to: "/devices", label: "Devices & notifications", icon: <DevicesIcon /> },
  { to: "/admins", label: "Admins", icon: <AdminIcon /> },
];

function LiveIndicator() {
  const mode = useDataMode();
  const { ready } = useAdminData();
  const loading = Object.values(ready).some((r) => !r);
  const label = mode === "demo" ? "Demo data" : loading ? "Connecting…" : "Live";
  const color = mode === "demo" ? "warning.main" : loading ? "text.disabled" : "success.main";
  return (
    <div className="flex items-center gap-2" role="status" aria-live="polite">
      <Box component="span" className="inline-block h-2.5 w-2.5 rounded-full" sx={{ bgcolor: color }} />
      <Typography variant="body2" fontWeight={600} sx={{ color }}>
        {label}
      </Typography>
    </div>
  );
}

function ThemeToggle() {
  const { mode, systemMode, setMode } = useColorScheme();
  const current = mode === "system" ? systemMode : mode;
  if (!current) return null;
  const next = current === "dark" ? "light" : "dark";
  return (
    <Tooltip title={`Switch to ${next} mode`}>
      <IconButton onClick={() => setMode(next)} aria-label={`Switch to ${next} mode`}>
        {current === "dark" ? <LightIcon /> : <DarkIcon />}
      </IconButton>
    </Tooltip>
  );
}

function AccountMenu() {
  const { viewer, signOut } = useAuth();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  if (!viewer) return null;
  const initials = (viewer.name || viewer.email || "?").split(/\s+/).map((p) => p[0]).slice(0, 2).join("").toUpperCase();
  return (
    <>
      <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label="Account" size="small">
        <Avatar src={viewer.photoURL ?? undefined} sx={{ width: 34, height: 34, bgcolor: "primary.main", fontSize: 14 }}>
          {initials}
        </Avatar>
      </IconButton>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
        <div className="px-4 py-2">
          <Typography variant="subtitle2">{viewer.name || "Admin"}</Typography>
          <Typography variant="body2" color="text.secondary">
            {viewer.email}
          </Typography>
        </div>
        <Divider />
        <MenuItem
          onClick={async () => {
            setAnchor(null);
            await signOut();
          }}
        >
          Sign out
        </MenuItem>
      </Menu>
    </>
  );
}

export function AdminLayout() {
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up("md"));
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();
  const mode = useDataMode();

  const nav = (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 px-5 py-5">
        <PinIcon sx={{ color: "primary.main" }} />
        <div>
          <Typography variant="subtitle1" fontWeight={700} lineHeight={1.1}>
            YatraMitra
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Admin panel
          </Typography>
        </div>
      </div>
      <List component="nav" aria-label="Main" className="px-2">
        {NAV.map((item) => {
          const active = item.to === "/" ? location.pathname === "/" : location.pathname.startsWith(item.to);
          return (
            <ListItemButton
              key={item.to}
              component={NavLink}
              to={item.to}
              selected={active}
              onClick={() => setMobileOpen(false)}
              sx={{ borderRadius: 2, mb: 0.5 }}
            >
              <ListItemIcon sx={{ minWidth: 38, color: active ? "primary.main" : undefined }}>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} slotProps={{ primary: { fontWeight: active ? 600 : 500, fontSize: 14 } }} />
            </ListItemButton>
          );
        })}
      </List>
      <div className="mt-auto px-5 py-4">
        <Typography variant="caption" color="text.secondary">
          Firestore project: yatramitra121189
        </Typography>
      </div>
    </div>
  );

  return (
    <Box className="flex min-h-full" sx={{ bgcolor: "background.default" }}>
      <AppBar
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{ width: { md: `calc(100% - ${DRAWER_WIDTH}px)` }, ml: { md: `${DRAWER_WIDTH}px` }, borderBottom: 1, borderColor: "divider" }}
      >
        <Toolbar className="gap-2">
          {!desktop && (
            <IconButton edge="start" onClick={() => setMobileOpen(true)} aria-label="Open navigation">
              <MenuIcon />
            </IconButton>
          )}
          <LiveIndicator />
          <div className="flex-1" />
          <ThemeToggle />
          <AccountMenu />
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        {desktop ? (
          <Drawer variant="permanent" open slotProps={{ paper: { sx: { width: DRAWER_WIDTH, boxSizing: "border-box" } } }}>
            {nav}
          </Drawer>
        ) : (
          <Drawer
            variant="temporary"
            open={mobileOpen}
            onClose={() => setMobileOpen(false)}
            ModalProps={{ keepMounted: true }}
            slotProps={{ paper: { sx: { width: DRAWER_WIDTH } } }}
          >
            {nav}
          </Drawer>
        )}
      </Box>

      <Box component="main" className="min-w-0 flex-1 px-4 pb-10 pt-20 sm:px-6 lg:px-8">
        {mode === "demo" && (
          <Alert
            severity="warning"
            className="mb-5"
            action={
              <Button color="inherit" size="small" onClick={leaveDemoMode}>
                Leave demo
              </Button>
            }
          >
            You're looking at made-up demo data. Nothing here comes from or changes your real Firestore.
          </Alert>
        )}
        <Outlet />
      </Box>
    </Box>
  );
}
