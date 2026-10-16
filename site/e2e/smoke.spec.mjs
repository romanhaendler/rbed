// The site's one browser test: on a concept page and on a domain page, moving a control runs
// rbed again and changes the Event table.
import { expect, test } from '@playwright/test';

for (const { path, control } of [
  { path: './', control: 'Threshold' },
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
