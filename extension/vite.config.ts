import { defineConfig } from "vite";
import preact from "@preact/preset-vite";

// .env lives at the repo root (see ../.env.example), not inside extension/
export default defineConfig({
  plugins: [preact()],
  envDir: "../",
  envPrefix: ["FIREBASE_", "GOOGLE_"],
  build: {
    rollupOptions: {
      input: {
        "hal-popup": "hal-popup.html",
        "hal-background": "src/hal-background.ts",
      },
      output: {
        entryFileNames: "[name].js",
      },
    },
  },
});
