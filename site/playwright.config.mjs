// The browser smoke test (e2e/), against the built site: run `npm run build` first. PORT serves it
// on another port than 4173, e.g. while a second checkout's tests run.
import { defineConfig } from '@playwright/test';

const url = `http://localhost:${process.env.PORT ?? 4173}/rbed/`;

export default defineConfig({
  testDir: 'e2e',
  use: { baseURL: url },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: {
    command: `npm run preview -- --strictPort --port ${process.env.PORT ?? 4173}`,
    url,
    reuseExistingServer: !process.env.CI,
  },
});
