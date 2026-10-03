import { defineConfig } from "vitest/config";

// Security-rules tests: run against the local Firestore emulator via `npm run test:rules`.
export default defineConfig({
  test: {
    include: ["rules-test/**/*.test.ts"],
    environment: "node",
    testTimeout: 30_000,
    hookTimeout: 30_000,
    fileParallelism: false,
  },
});
