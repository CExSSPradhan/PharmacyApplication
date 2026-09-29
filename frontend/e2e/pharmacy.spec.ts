import { test, expect } from '@playwright/test';
const email = process.env.OWNER_EMAIL || 'owner@pharmacy.local';
const password = process.env.OWNER_PASSWORD;
test.beforeEach(async ({ page }) => {
  if (!password) throw new Error('OWNER_PASSWORD must be configured');
  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
});
test('purchase → stock → FEFO sale → cancellation restores exact inventory', async ({ page }) => {
  const name = `E2E medicine ${Date.now()}`;
  await page.getByRole('link', { name: 'Medicines', exact: true }).click();
  await page.getByRole('button', { name: 'Add medicine', exact: true }).click();
  await page.getByLabel('Medicine name').fill(name);
  await page.getByLabel('Strength', { exact: true }).fill('500 mg');
  await page.getByLabel('Low-stock threshold').fill('5');
  await page.getByRole('button', { name: 'Create medicine', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('link', { name: 'Purchases', exact: true }).click();
  await page.getByRole('link', { name: 'Record purchase', exact: true }).click();
  await page
    .getByRole('combobox', { name: 'Supplier', exact: true })
    .selectOption({ label: 'Medline Distribution' });
  await page.getByLabel('Invoice number', { exact: true }).fill(`E2E-${Date.now()}`);
  await page.getByLabel('Find medicine').fill(name);
  await page.getByRole('button', { name: new RegExp(name) }).click();
  await page.getByLabel('Quantity', { exact: true }).fill('10');
  await page.getByLabel('Batch number', { exact: true }).fill('E2E-EARLY');
  const expiry = new Date();
  expiry.setDate(expiry.getDate() + 60);
  await page.getByLabel('Expiry date', { exact: true }).fill(expiry.toISOString().slice(0, 10));
  await page.getByLabel('Purchase price (₹)', { exact: true }).fill('2.50');
  await page.getByLabel('Selling price (₹)', { exact: true }).fill('4.25');
  await page.getByRole('button', { name: 'Save purchase & update stock', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('E2E-EARLY');
  await page.getByRole('button', { name: 'Done', exact: true }).click();
  await page.getByRole('link', { name: 'Inventory', exact: true }).click();
  await page.getByLabel('Search inventory').fill(name);
  await expect(page.getByRole('row').filter({ hasText: name })).toContainText('10 units');
  await page.getByRole('link', { name: 'Sales', exact: true }).click();
  await page.getByRole('link', { name: 'New sale', exact: true }).click();
  await page.getByLabel('Find medicine').fill(name);
  await page.getByRole('button', { name: new RegExp(name) }).click();
  await page.getByLabel('Quantity', { exact: true }).fill('3');
  await page.getByRole('button', { name: 'Complete sale', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('₹12.75');
  await page.getByRole('button', { name: 'Cancel sale', exact: true }).click();
  await page.getByLabel('Cancellation reason').fill('E2E verification: restore stock');
  await page.getByRole('button', { name: 'Confirm cancellation', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('CANCELLED');
  await page.getByRole('button', { name: 'Done', exact: true }).click();
  await page.getByRole('link', { name: 'Inventory', exact: true }).click();
  await page.getByLabel('Search inventory').fill(name);
  await expect(page.getByRole('row').filter({ hasText: name })).toContainText('10 units');
  await page.getByRole('button', { name: 'View batches', exact: true }).click();
  await page.getByRole('button', { name: 'View movements', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('SALE_CANCEL_RESTORE');
  await expect(page.getByRole('dialog')).toContainText('SALE_OUT');
  await expect(page.getByRole('dialog')).toContainText('PURCHASE_IN');
});
test('dashboard renders without browser errors and navigation works on mobile', async ({
  page,
}) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.screenshot({ path: '../.runtime/dashboard-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Open navigation' }).click();
  await page.getByRole('link', { name: 'Reports', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Reports', exact: true })).toBeVisible();
  await page.screenshot({ path: '../.runtime/reports-mobile.png', fullPage: true });
  expect(errors).toEqual([]);
});
