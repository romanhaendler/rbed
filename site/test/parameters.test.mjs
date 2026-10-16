// Parameter values in the page address: only those that differ from the default are written,
// and reading an address gives back exactly what was written.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fromQuery, toQuery } from '../src/parameters.ts';

const PARAMETERS = [
  { id: 'threshold', default: 80 },
  { id: 'duration', default: 30 },
];

test('an address without parameters gives the defaults', () => {
  assert.deepEqual(fromQuery('', PARAMETERS), { threshold: 80, duration: 30 });
});

test('a parameter in the address overrides its default, unknown ones are ignored', () => {
  assert.deepEqual(fromQuery('?threshold=72.5&x=1', PARAMETERS), { threshold: 72.5, duration: 30 });
});

test('a value that is no finite number is passed on as NaN, for rbed to reject', () => {
  assert.ok(Number.isNaN(fromQuery('?threshold=abc', PARAMETERS).threshold));
  assert.ok(Number.isNaN(fromQuery('?threshold=Infinity', PARAMETERS).threshold));
  assert.equal(fromQuery('?threshold=', PARAMETERS).threshold, 80);
});

test('only values that differ from the default go into the address, other keys stay', () => {
  assert.equal(toQuery('?x=1&threshold=70', { threshold: 80, duration: 40 }, PARAMETERS), '?x=1&duration=40');
  assert.equal(toQuery('?threshold=70', { threshold: 80, duration: 30 }, PARAMETERS), '');
});

test('reading back what was written gives the same values', () => {
  const values = { threshold: 61, duration: 0.25 };
  assert.deepEqual(fromQuery(toQuery('', values, PARAMETERS), PARAMETERS), values);
});
