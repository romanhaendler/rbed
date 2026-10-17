// The site's browser smoke tests: every page of the manifest loads at desktop and phone width
// without a page error or sideways scrolling; on a concept page and on a domain page, moving a
// control runs rbed again and changes the Event table; drawing a Channel changes the Java source;
// on a phone, the Event tables fit without sideways scrolling, every plot keeps about 200 px, and
// the chart stays in view while a control moves; the front page's chart is above the fold, and an
// Event table stands between its chart and its source; logistics starts with door hidden; an
// address with a fragment lands on its section; Tab first reaches the skip link; a chosen search
// result closes the search; Reset keeps the focus in the panel.
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
  const code = page.locator('.scenarioCode');
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

test('on a phone every chart keeps a plot about 200 px wide', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  for (const { path } of PAGES) {
    await page.goto(path.slice(1));
    await expect(page.locator('.shellContent h1')).toBeVisible();
    for (const plot of await page.locator('.drawStage .uc-axis-x.uc-axis-bottom').all()) {
      expect((await plot.boundingBox()).width, path).toBeGreaterThanOrEqual(190);
    }
  }
});

test('the front page shows its chart above the fold, and a Scenario its Event table between chart and source', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('');
  const chart = page.locator('.drawStage').first();
  await expect(chart).toBeVisible();
  expect((await chart.boundingBox()).y).toBeLessThan(800 - 150);
  const table = (await page.locator('#events').boundingBox()).y;
  expect(table).toBeGreaterThan((await chart.boundingBox()).y);
  expect(table).toBeLessThan((await page.locator('.scenarioCode').boundingBox()).y);
});

test('the logistics chart starts with door hidden: its DoorOpen lane says the same', async ({ page }) => {
  await page.goto('logistics/');
  await expect(page.getByRole('checkbox', { name: 'door', exact: true })).not.toBeChecked();
  await expect(page.getByRole('checkbox', { name: 'temp', exact: true })).toBeChecked();
});

test('an address with a fragment lands on its section once the app has drawn the page', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto('cranes/#events');
  await expect(page.locator('.uc-root canvas').first()).toBeVisible();
  await expect.poll(() => page.locator('#events').evaluate((e) => Math.round(e.getBoundingClientRect().top))).toBeLessThan(200);
});

test('on a phone the chart stays in view while a control under it moves, and the change is announced', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 });
  await page.goto('manufacturing/');
  const slider = page.getByRole('slider', { name: 'Fast heating above' });
  await slider.focus();
  await slider.press('ArrowRight');
  const chart = await page.locator('.drawStage').boundingBox();
  const control = await slider.boundingBox();
  expect(chart.y).toBeGreaterThanOrEqual(56);
  expect(control.y).toBeGreaterThan(chart.y + chart.height);
  expect(control.y + control.height).toBeLessThanOrEqual(800);
  await expect(page.getByRole('status').filter({ hasText: /Events?/ })).toHaveText(/\d+ Events?/);
});

test('the first Tab stop skips to the content', async ({ page }) => {
  await page.goto('cranes/');
  await expect(page.locator('.uc-root canvas').first()).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused();
});

test('the Javadoc has the site’s fonts and a link back to the site', async ({ page }) => {
  await page.goto('api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html');
  expect(await page.evaluate(() => getComputedStyle(document.body).fontFamily)).toContain('Geist Sans');
  await page.getByRole('link', { name: 'rbed documentation' }).click();
  await expect(page).toHaveURL(/\/rbed\/$/);
});

test('choosing a search result closes the palette and focuses the chosen page', async ({ page }) => {
  await page.goto('cranes/');
  await expect(page.locator('.uc-root canvas').first()).toBeVisible();
  await page.getByRole('button', { name: 'Search' }).click();
  await page.keyboard.type('Data gaps');
  await page.keyboard.press('Enter');
  await expect(page.locator('.shellContent h1')).toHaveText(PAGES.find((p) => p.path === '/data-gaps/').title);
  await expect(page.getByRole('dialog', { name: 'Search the documentation' })).toBeHidden();
  await expect(page.locator('.shellContent h1')).toBeFocused();
});

test('Reset moves the focus to the first control, not to the page', async ({ page }) => {
  await page.goto('event-lifecycle/');
  const slider = page.getByRole('slider').first();
  await slider.focus();
  await slider.press('ArrowRight');
  await page.getByRole('button', { name: 'Reset' }).click();
  await expect(slider).toBeFocused();
});
