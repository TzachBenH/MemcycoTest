import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/**
 * End-to-end against the full Docker stack (nginx → backend-1 / backend-2 → PostgreSQL): the real UI, API,
 * Quartz cluster and database. Every run uses unique names and parameters, so it can be repeated against the same
 * database, and it deletes what it created.
 */

// Once a year (January 1st, 03:00): the scheduling never fires on its own during the test.
const YEARLY_CRON = '0 0 3 1 1 ?';

const stamp = `${Date.now()}`;
const created: string[] = [];

async function openNewSchedulingDialog(page: Page) {
  await page.getByRole('button', { name: 'New scheduling' }).first().click();
  const dialog = page.getByRole('dialog', { name: 'New scheduling' });
  await expect(dialog).toBeVisible();
  return dialog;
}

async function fillLogScheduling(page: Page, name: string, message: string) {
  const dialog = await openNewSchedulingDialog(page);
  await dialog.getByRole('textbox', { name: 'Name' }).fill(name);
  await dialog.getByRole('combobox', { name: 'Task' }).click();
  await page.getByRole('option', { name: 'Log Message' }).click();
  await dialog.getByRole('textbox', { name: 'Log message' }).fill(message);
  await dialog.getByRole('tab', { name: 'Cron' }).click();
  await dialog.getByRole('textbox', { name: 'Cron expression (Quartz)' }).fill(YEARLY_CRON);
  return dialog;
}

async function deleteByName(request: APIRequestContext, name: string) {
  const response = await request.get(`/api/schedulings?search=${encodeURIComponent(name)}&size=100`);
  if (!response.ok()) return;
  const page = (await response.json()) as { content: Array<{ id: string; name: string }> };
  for (const scheduling of page.content.filter((s) => s.name === name)) {
    await request.delete(`/api/schedulings/${scheduling.id}`);
  }
}

test.afterAll(async ({ request }) => {
  for (const name of created) {
    await deleteByName(request, name);
  }
});

test('create a Log scheduling, run it now and see the successful run in its history', async ({ page }) => {
  const name = `E2E log ${stamp}`;
  created.push(name);
  await page.goto('/');

  const dialog = await fillLogScheduling(page, name, `Hello from Playwright ${stamp}`);
  await dialog.getByRole('button', { name: 'Create' }).click();
  await expect(dialog).toBeHidden();

  // Find the new row (search keeps the test independent of other data in the table).
  await page.getByLabel('Search by name').fill(name);
  const row = page.getByRole('row').filter({ hasText: name });
  await expect(row).toHaveCount(1);

  await row.getByRole('button', { name: 'Run now' }).click();
  await row.getByRole('button', { name: 'History' }).click();

  await expect(page).toHaveURL(/\/schedulings\/[0-9a-f-]{36}\/history/);
  await expect(page.getByRole('heading', { name })).toBeVisible();
  const history = page.getByRole('table', { name: 'Execution history' });
  // The history polls every 10 s.
  await expect(history.getByText('Success')).toBeVisible({ timeout: 30_000 });
  await expect(history.getByRole('cell', { name: 'Manual', exact: true })).toBeVisible();
  // Executed by one of the two backend instances of the Quartz cluster.
  await expect(history.getByText(/backend-[12]/)).toBeVisible();
});

test('creating the same scheduling twice is refused as a duplicate', async ({ page }) => {
  const first = `E2E duplicate ${stamp}`;
  const second = `E2E duplicate copy ${stamp}`;
  created.push(first, second);
  const message = `Same message ${stamp}`;
  await page.goto('/');

  let dialog = await fillLogScheduling(page, first, message);
  await dialog.getByRole('button', { name: 'Create' }).click();
  await expect(dialog).toBeHidden();

  // Same task, parameters and timing under another name: still the same scheduling.
  dialog = await fillLogScheduling(page, second, message);
  await dialog.getByRole('button', { name: 'Create' }).click();
  await expect(dialog.getByText('Duplicate scheduling')).toBeVisible();
  await expect(dialog).toContainText(first);
  await dialog.getByRole('button', { name: 'Cancel' }).click();
  await expect(dialog).toBeHidden();
});
