// The browser smoke test (e2e/), against the built site: run `npm run build` first.
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: 'e2e',
  use: { baseURL: 'http://localhost:4173/rbed/' },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: {
    command: 'npm run preview -- --strictPort',
    url: 'http://localhost:4173/rbed/',
    reuseExistingServer: !process.env.CI,
  },
});
