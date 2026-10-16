// The site's browser smoke tests: on a concept page and on a domain page, moving a control runs
// rbed again and changes the Event table; on a phone, the Event tables fit without sideways
// scrolling.
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
