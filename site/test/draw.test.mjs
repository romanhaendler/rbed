import assert from 'node:assert/strict';
import { test } from 'node:test';
import { drawTo } from '../src/draw.ts';

const times = [0, 1, 2, 3, 4];

test('a point sets the Sample nearest in time and leaves the others', () => {
  assert.deepEqual(drawTo([5, 5, 5, 5, 5], times, 2.4, 9), { values: [5, 5, 9, 5, 5], last: [2, 9] });
});

test('a stroke fills the Samples it passed between two points on a straight line', () => {
  assert.deepEqual(drawTo([5, 5, 5, 5, 5], times, 4, 8, [0, 0]).values, [0, 2, 4, 6, 8]);
  assert.deepEqual(drawTo([5, 5, 5, 5, 5], times, 0.9, 1, [3, 7]).values, [5, 1, 4, 7, 5]);
});
