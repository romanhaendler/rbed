// The address of a page below the site's base, as the router and the build read it.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { markdownFile, pageAt, pathOf, titleOf } from '../src/pages.ts';

test('a path below the base is the page path, with or without its trailing slash', () => {
  assert.equal(pathOf('/rbed/', '/rbed/'), '/');
  assert.equal(pathOf('/rbed', '/rbed/'), '/');
  assert.equal(pathOf('/rbed/event-lifecycle/', '/rbed/'), '/event-lifecycle/');
  assert.equal(pathOf('/rbed/event-lifecycle', '/rbed/'), '/event-lifecycle/');
  assert.equal(pathOf('/event-lifecycle/', '/'), '/event-lifecycle/');
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

test('the document title names the page and the site', () => {
  assert.equal(titleOf({ path: '/', title: 'rbed' }), 'rbed: rule-based event detection');
  assert.equal(titleOf({ path: '/event-lifecycle/', title: 'Event lifecycle' }), 'Event lifecycle · rbed');
});
