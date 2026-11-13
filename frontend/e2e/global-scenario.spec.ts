import { expect, test, type Page } from '@playwright/test';
import path from 'node:path';

/**
 * Global acceptance scenario (prompt pack): admin provisions users, operator imports & confirms,
 * reviewer searches/maps and is denied mutations, operator reviews an issue and exports.
 * Requires demo accounts admin@/operator@/reviewer@landstack.demo with LANDSTACK_DEMO_PASSWORD.
 */
const PASSWORD = process.env.E2E_PASSWORD ?? process.env.LANDSTACK_DEMO_PASSWORD ?? '';
const SAMPLE = path.resolve(process.cwd(), '../sample-data/bihar_parcels_sample.csv');
const MALFORMED = path.resolve(process.cwd(), '../sample-data/invalid/pdf_renamed_as.csv');

async function login(page: Page, email: string) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(PASSWORD);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
}

async function logout(page: Page) {
  await page.getByRole('button', { name: /sign out/i }).click();
  await expect(page).toHaveURL(/\/login/);
}

test.describe.serial('LandStack global acceptance scenario', () => {
  test.skip(!PASSWORD, 'Set E2E_PASSWORD (the demo account password) to run end-to-end tests.');
  const stamp = Date.now();

  test('administrator provisions an operator and a reviewer', async ({ page }) => {
    await login(page, 'admin@landstack.demo');
    await page.getByRole('link', { name: 'User management' }).click();
    for (const role of ['Data operator', 'Read-only reviewer']) {
      await page.getByRole('button', { name: 'Add user' }).click();
      await page.getByLabel('Email').fill(`${role.split(' ')[0]!.toLowerCase()}-${stamp}@test.local`);
      await page.getByLabel('Display name').fill(`E2E ${role}`);
      await page.getByLabel('Initial password').fill('E2e-password-123');
      await page.getByLabel('Role').selectOption({ label: role });
      await page.getByRole('button', { name: 'Create user' }).click();
      await expect(page.getByText(`E2E ${role}`)).toBeVisible();
    }
    await logout(page);
  });

  test('operator uploads, maps, reviews and confirms a CSV', async ({ page }) => {
    await login(page, 'operator@landstack.demo');
    await page.getByRole('link', { name: 'Import datasets' }).click();
    await page.getByLabel('Select dataset file').setInputFiles(SAMPLE);
    await page.getByRole('button', { name: 'Upload' }).click();
    await page.getByRole('button', { name: 'Open import' }).click();
    await page.getByRole('tab', { name: 'Field mapping' }).click({ timeout: 30_000 });
    await expect(page.getByText('Suggested · not confirmed').first()).toBeVisible();
    await page.getByRole('button', { name: /save mapping/i }).click();
    await expect(page.getByText('Preview: source vs normalized')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText('Blocked').first()).toBeVisible();
    await page.getByRole('button', { name: 'Confirm import…' }).click();
    await page.getByRole('button', { name: /^Save \d+ records$/ }).click();
    await expect(page.getByText(/Import completed/)).toBeVisible({ timeout: 30_000 });
    await logout(page);
  });

  test('reviewer searches, opens provenance, views map and cannot mutate', async ({ page }) => {
    await login(page, 'reviewer@landstack.demo');
    await expect(page.getByRole('link', { name: 'Import datasets' })).toHaveCount(0);
    await page.getByRole('link', { name: 'Land records' }).click();
    await page.getByLabel('Search records').fill('BR');
    const first = page.locator('tbody tr').first().getByRole('link');
    await first.click();
    await expect(page.getByText('Provenance')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Correct' })).toHaveCount(0);
    await page.getByRole('link', { name: 'Map view' }).click();
    await expect(page.getByText('Not mappable', { exact: true })).toBeVisible();
    const status = await page.evaluate(async () => (await fetch('/api/v1/exports/land-records.csv', { credentials: 'include' })).status);
    expect(status).toBe(403);
    await logout(page);
  });

  test('operator dismisses an issue with a note and exports CSV', async ({ page }) => {
    await login(page, 'operator@landstack.demo');
    await page.getByRole('link', { name: 'Data quality' }).click();
    await page.locator('ul li button').first().click();
    await page.getByRole('button', { name: 'Dismiss' }).click();
    await page.getByLabel(/Note/).fill('Checked against the synthetic source; acceptable for the demo.');
    await page.getByRole('button', { name: 'Save review' }).click();
    await expect(page.getByText('Review recorded.')).toBeVisible();
    await page.getByRole('link', { name: 'Land records' }).click();
    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export CSV' }).click();
    expect((await download).suggestedFilename()).toMatch(/\.csv$/);
  });

  test('malformed file is rejected with an actionable message', async ({ page }) => {
    await login(page, 'operator@landstack.demo');
    await page.getByRole('link', { name: 'Import datasets' }).click();
    await page.getByLabel('Select dataset file').setInputFiles(MALFORMED);
    await page.getByRole('button', { name: 'Upload' }).click();
    await expect(page.getByText(/does not match its extension/)).toBeVisible();
  });
});
