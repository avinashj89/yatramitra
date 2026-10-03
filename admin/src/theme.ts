import { createTheme } from "@mui/material/styles";
import type {} from "@mui/x-data-grid/themeAugmentation";
import type {} from "@mui/x-charts/themeAugmentation";

// Same orange accent as the YatraMitra app. Light and dark follow the system (or the toggle in the
// top bar); MUI adds a .light/.dark class on <html> that Tailwind's dark: variant also uses.
export const theme = createTheme({
  cssVariables: { colorSchemeSelector: "class" },
  colorSchemes: {
    light: {
      palette: {
        primary: { main: "#C2410C", light: "#F97316", dark: "#9A3412", contrastText: "#FFFFFF" },
        secondary: { main: "#0F766E" },
        background: { default: "#F6F7F9", paper: "#FFFFFF" },
      },
    },
    dark: {
      palette: {
        primary: { main: "#FB923C", light: "#FDBA74", dark: "#EA580C", contrastText: "#1C1206" },
        secondary: { main: "#2DD4BF" },
        background: { default: "#0F1115", paper: "#171A21" },
      },
    },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: '"Inter", ui-sans-serif, system-ui, sans-serif',
    h4: { fontWeight: 700, letterSpacing: "-0.02em" },
    h5: { fontWeight: 700, letterSpacing: "-0.01em" },
    h6: { fontWeight: 600 },
    button: { textTransform: "none", fontWeight: 600 },
  },
  components: {
    MuiCard: { defaultProps: { variant: "outlined" } },
    MuiPaper: { styleOverrides: { outlined: { borderColor: "var(--mui-palette-divider)" } } },
    MuiButton: { defaultProps: { disableElevation: true } },
    MuiDataGrid: {
      styleOverrides: {
        root: { border: "none" },
        columnHeaderTitle: { fontWeight: 600 },
      },
    },
  },
});
