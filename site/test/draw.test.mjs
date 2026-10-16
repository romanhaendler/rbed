import assert from 'node:assert/strict';
import { test } from 'node:test';
import { drawnSource, drawTo } from '../src/draw.ts';

const times = [0, 1, 2, 3, 4];

test('a point sets the Sample nearest in time and leaves the others', () => {
  assert.deepEqual(drawTo([5, 5, 5, 5, 5], times, 2.4, 9), { values: [5, 5, 9, 5, 5], last: [2, 9] });
});

test('a stroke fills the Samples it passed between two points on a straight line', () => {
  assert.deepEqual(drawTo([5, 5, 5, 5, 5], times, 4, 8, [0, 0]).values, [0, 2, 4, 6, 8]);
  assert.deepEqual(drawTo([5, 5, 5, 5, 5], times, 0.9, 1, [3, 7]).values, [5, 1, 4, 7, 5]);
});

const loop = 'Rule r = Rule.named("R");\n\ndouble[] values = {70, 72, 75};\nfor (int i = 0; i < values.length; i++) {}\n';

test('without a drawn Channel the source stays as it is', () => {
  assert.equal(drawnSource(loop, {}), loop);
});

test("the drawn values replace the source's array of the Sample values", () => {
  assert.equal(
    drawnSource(loop, { temp: [70, 80.5, 75] }),
    'Rule r = Rule.named("R");\n\ndouble[] values = {70, 80.5, 75};\nfor (int i = 0; i < values.length; i++) {}\n',
  );
  const many = Array.from({ length: 17 }, (_, i) => i);
  assert.equal(
    drawnSource('  double[] values = {0, 0,\n      0};', { temp: many }),
    '  double[] values = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,\n      15, 16};',
  );
});

test("the drawn values replace a source's pushes when it pushes every Sample", () => {
  const pushes =
    'detector.push(1_000, Map.of(temp, 75.0));   // timestamp in ms\ndetector.push(2_000, Map.of(temp, 85.0));\n';
  assert.equal(
    drawnSource(pushes, { temp: [60, 92.5] }),
    'detector.push(1_000, Map.of(temp, 60.0));   // timestamp in ms\ndetector.push(2_000, Map.of(temp, 92.5));\n',
  );
});

test('a source that does not show every Sample says the values were drawn', () => {
  const some = 'detector.push(0, Map.of(power, 0.0));   // one Sample a minute\n';
  assert.equal(
    drawnSource(some, { power: [1, 2, 3] }),
    some + '// The values of power are the ones drawn on the chart.\n',
  );
});
