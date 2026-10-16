// The address of a page below the site's base, as the router and the build read it.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { markdownFile, PAGES, pageAt, pathOf, titleOf } from '../src/pages.ts';

test('a path below the base is the page path, with or without its trailing slash', () => {
  assert.equal(pathOf('/rbed/', '/rbed/'), '/');
  assert.equal(pathOf('/rbed', '/rbed/'), '/');
  assert.equal(pathOf('/rbed/event-lifecycle/', '/rbed/'), '/event-lifecycle/');
  assert.equal(pathOf('/rbed/event-lifecycle', '/rbed/'), '/event-lifecycle/');
  assert.equal(pathOf('/event-lifecycle/', '/'), '/event-lifecycle/');
});

test("a page's index.html, as GitHub Pages also serves it, is the page", () => {
  assert.equal(pathOf('/rbed/cranes/index.html', '/rbed/'), '/cranes/');
  assert.equal(pathOf('/rbed/index.html', '/rbed/'), '/');
});

test('a path outside the base is no page path', () => {
  assert.equal(pathOf('/other/', '/rbed/'), undefined);
  assert.equal(pathOf('/rbedx/', '/rbed/'), undefined);
});

test('the front page is at the base, an unknown path is no page', () => {
  assert.equal(pageAt('/rbed/', '/rbed/')?.path, '/');
  assert.equal(pageAt('/rbed/no-such-page/', '/rbed/'), undefined);
});

test('a page reads its Markdown from the file named after its path, which is also its twin', () => {
  assert.equal(markdownFile({ path: '/' }), 'index.md');
  assert.equal(markdownFile({ path: '/event-lifecycle/' }), 'event-lifecycle.md');
});

test("every link from one page to another names a page of the manifest or the site's Javadoc", () => {
  for (const page of PAGES) {
    const markdown = readFileSync(new URL(`../src/content/${markdownFile(page)}`, import.meta.url), 'utf8');
    for (const [, link] of markdown.matchAll(/\]\(((?!https?:|mailto:|#)[^)\s]+)\)/g)) {
      const target = new URL(link, `https://site.example/rbed${page.path}`).pathname;
      if (target.startsWith('/rbed/api/')) continue; // the build checks these against the Javadoc
      assert.ok(pageAt(target, '/rbed/') !== undefined, `${page.path} links ${link}`);
    }
  }
});

test('the document title names the page and the site', () => {
  assert.equal(titleOf({ path: '/', title: 'rbed' }), 'rbed: rule-based event detection');
  assert.equal(titleOf({ path: '/event-lifecycle/', title: 'Event lifecycle' }), 'Event lifecycle · rbed');
});
