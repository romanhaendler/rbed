// The site's browser smoke tests: every page of the manifest loads at desktop and phone width
// without a page error or sideways scrolling; on a concept page and on a domain page, moving a
// control runs rbed again and changes the Event table; drawing a Channel changes the Java source;
// on a phone, the Event tables fit without sideways scrolling.
import { expect, test } from '@playwright/test';
import { PAGES } from '../src/pages.ts';

for (const width of [1280, 375]) {
  test(`every page loads at ${width} px without a page error or sideways scrolling`, async ({ page }) => {
    await page.setViewportSize({ width, height: 800 });
    const errors = [];
    page.on('pageerror', (e) => errors.push(`${page.url()}: ${e.message}`));
    for (const { path } of PAGES) {
      await page.goto(path.slice(1));
      await expect(page.locator('.shellContent h1')).toBeVisible();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${path} scrolls sideways`).toBeLessThanOrEqual(0);
    }
    expect(errors).toEqual([]);
  });
}

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
  // A level stroke across the whole plot (its rectangle as plotArea.ts reads it, the ends past
  // it, where the values are clamped) gives every Sample the same value, whatever the margins.
  const stage = page.locator('.drawStage');
  const x = await stage.locator('.uc-axis-x.uc-axis-bottom').boundingBox();
  const y = await stage.locator('.uc-axis-y.uc-axis-left').boundingBox();
  await page.mouse.move(x.x - 4, y.y + y.height * 0.1);
  await page.mouse.down();
  await page.mouse.move(x.x + x.width + 4, y.y + y.height * 0.1, { steps: 20 });
  await page.mouse.up();
  await expect(code).not.toContainText('double[] values = {70, 72, 75, 85,');
  const values = /double\[\] values = \{([^}]*)\}/.exec(await code.innerText())[1].split(/,\s*/);
  expect(new Set(values).size, values.join(', ')).toBe(1);
  expect(Number(values[0])).toBeGreaterThan(85);
});

test('an unknown address shows that there is no page, an index.html address its page', async ({ page }) => {
  await page.goto('no-such-page/');
  await expect(page.locator('.shellContent h1')).toHaveText('No page at this address');
  await page.goto('cranes/index.html');
  await expect(page.locator('.shellContent h1')).toHaveText('Cranes');
});

test('a link to another page moves without a reload, and Back restores the page and its parameters', async ({ page }) => {
  await page.goto('?threshold=70');
  await expect(page.getByRole('slider', { name: 'Threshold' })).toHaveValue('70');
  await page.evaluate(() => (window.loadedOnce = true));
  await page.locator('.shellBody .rail a[href$="/event-lifecycle/"]').click();
  await expect(page.locator('.shellContent h1')).toHaveText(PAGES.find((p) => p.path === '/event-lifecycle/').title);
  expect(await page.evaluate(() => window.loadedOnce)).toBe(true);
  await page.goBack();
  await expect(page.locator('.shellContent h1')).toHaveText('rbed');
  await expect(page.getByRole('slider', { name: 'Threshold' })).toHaveValue('70');
  expect(await page.evaluate(() => window.loadedOnce)).toBe(true);
});

test.describe('without JavaScript', () => {
  test.use({ javaScriptEnabled: false });

  test('a page has its text, the header and every page in the navigation', async ({ page }) => {
    await page.goto('end-conditions/');
    await expect(page.locator('h1')).toHaveText(PAGES.find((p) => p.path === '/end-conditions/').title);
    await expect(page.locator('.shellHead')).toBeVisible();
    await expect(page.locator('.rail .railEntry')).toHaveCount(PAGES.length);
    await expect(page.getByText('The live chart and its controls need JavaScript.')).toBeVisible();
  });

  test('404.html says there is no page and lists the pages', async ({ page }) => {
    await page.goto('404.html');
    await expect(page.locator('h1')).toHaveText('No page at this address');
    await expect(page.locator('.rail .railEntry')).toHaveCount(PAGES.length);
  });
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
