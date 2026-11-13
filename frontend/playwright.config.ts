import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests run against a running stack (docker compose up, or backend + `npm run dev`)
 * with the dev demo accounts seeded (LANDSTACK_DEMO_PASSWORD). See docs/test-plan.md.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  retries: 0,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  reporter: [['list'], ['html', { open: 'never' }]],
});
