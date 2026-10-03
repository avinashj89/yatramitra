import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5174, strictPort: true },
  build: {
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      output: {
        // Keeps the big libraries in their own cached files.
        manualChunks: {
          firebase: ["firebase/app", "firebase/auth", "firebase/firestore"],
          mui: ["@mui/material", "@emotion/react", "@emotion/styled"],
          grid: ["@mui/x-data-grid"],
          charts: ["@mui/x-charts"],
        },
      },
    },
  },
  test: {
    include: ["test/**/*.test.ts"],
    environment: "node",
  },
});
