// The address of a page below the site's base, as the router and the build read it.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { pageAt, pathOf } from '../src/pages.ts';

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
