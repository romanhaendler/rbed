// The site's browser smoke tests: on a concept page and on a domain page, moving a control runs
// rbed again and changes the Event table; drawing a Channel changes the Java source; on a phone,
// the Event tables fit without sideways scrolling.
import { expect, test } from '@playwright/test';

for (const { path, control } of [
  { path: 'aggregates/', control: 'Driving above' },
  { path: 'cranes/', control: 'Lifted above' },
]) {
  test(`moving "${control}" on ${path} changes the Event table`, async ({ page }) => {
    const errors = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await page.goto(path);
    const events = page.locator('#events tbody');
    await expect(events).not.toContainText('No Event');
    const before = await events.innerText();
    await page.getByRole('slider', { name: control }).press('End');
    await expect(events).not.toHaveText(before);
    expect(errors).toEqual([]);
  });
}

test('drawing a Channel puts the drawn values into the Java source', async ({ page }) => {
  await page.goto('event-lifecycle/');
  const code = page.locator('.configuratorCode');
  await expect(code).toContainText('double[] values = {70, 72, 75, 85,');
  await page.getByRole('radiogroup', { name: 'Draw' }).locator('label', { hasText: 'temp' }).click();
  const box = await page.locator('.drawStage').boundingBox();
  await page.mouse.move(box.x + box.width * 0.2, box.y + box.height * 0.1);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.5, box.y + box.height * 0.1, { steps: 5 });
  await page.mouse.up();
  await expect(code).not.toContainText('double[] values = {70, 72, 75, 85,');
  await expect(code).toContainText('double[] values = {70,');
});

test('on a phone, the Event and Discarded tables need no sideways scrolling', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  // a Note and Truncated ends; long Rule names; Discarded Pending Events
  for (const path of ['automotive/', 'manufacturing/', 'event-lifecycle/']) {
    await page.goto(path);
    await expect(page.locator('#events')).toBeVisible();
    for (const table of await page.locator('#events, #discarded').all()) {
      const overflow = await table.evaluate((t) => t.scrollWidth - t.parentElement.clientWidth);
      expect(overflow, `${path} #${await table.getAttribute('id')}`).toBeLessThanOrEqual(0);
    }
  }
});
