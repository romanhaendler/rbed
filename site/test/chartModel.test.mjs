import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  channelAxesOf,
  channelBands,
  expressionKey,
  expressionRows,
  hasGaps,
  heldRows,
  timeTicks,
  undefinedStretches,
  windowStretches,
} from '../src/chartModel.ts';

const scenario = (channels, pulse = []) => ({ id: 's', channels, pulse, rules: ['R'], timeUnit: { name: 's', millis: 1000 } });
const rows = (columns) => columns[Object.keys(columns)[0]].map((_, i) => ({ t: i, ...Object.fromEntries(Object.entries(columns).map(([c, v]) => [c, v[i]])) }));

test('Channels of one scale share a band; one of a very different scale gets its own', () => {
  assert.deepEqual(channelBands(scenario(['hr', 'spo2']), rows({ hr: [80, 120], spo2: [97, 86] })), [['hr', 'spo2']]);
  assert.deepEqual(channelBands(scenario(['speed', 'rpm']), rows({ speed: [0, 20], rpm: [800, 0] })), [['speed'], ['rpm']]);
  assert.deepEqual(
    channelBands(scenario(['errors', 'cpu', 'speed']), rows({ errors: [3, 12], cpu: [50, 95], speed: [4, 9] })),
    [['errors', 'speed'], ['cpu']],
  );
});

test('a Pulse Channel is drawn on the first band', () => {
  assert.deepEqual(
    channelBands(scenario(['errors', 'cpu', 'deploy'], ['deploy']), rows({ errors: [3, 12], cpu: [50, 95], deploy: [1, null] })),
    [['errors', 'deploy'], ['cpu']],
  );
});

test('each band has its own axis over its own Channels and thresholds, the first band on top', () => {
  const r = rows({ speed: [0, 20], rpm: [800, 0] });
  const [speed, rpm] = channelAxesOf({
    scenario: scenario(['speed', 'rpm']),
    rows: r,
    rules: ['R'],
    expressions: [],
    expressionRows: [],
    thresholds: [{ channel: 'rpm', rule: 'R', condition: 'when >', value: 1000 }],
  });
  assert.deepEqual(speed.ticks, [0, 5, 10, 15, 20]);
  assert.deepEqual(rpm.ticks, [0, 500, 1000]);
  // 7 units: the lane, rpm's band above it, speed's band on top.
  const at = (axis, v) => (v - axis.domain[0]) / (axis.domain[1] - axis.domain[0]);
  assert.ok(at(speed, 0) > 4 / 7 && at(speed, 20) < 1);
  assert.ok(at(rpm, 0) > 1 / 7 && at(rpm, 1000) < 4 / 7);
});

test('the Expressions are read at every Sample and advance, and undefined where any of them is', () => {
  const plotted = expressionRows(
    [
      { t: 0, [expressionKey(0)]: null, [expressionKey(1)]: 1 },
      { t: 2, [expressionKey(0)]: 5, [expressionKey(1)]: 2 },
    ],
    [
      { name: 'a', threshold: null, values: [], atAdvances: [7] },
      { name: 'b', threshold: null, values: [], atAdvances: [null] },
    ],
    [1000],
    1000,
  );
  assert.deepEqual(plotted, [
    { t: 0, [expressionKey(0)]: null, [expressionKey(1)]: 1 },
    { t: 1, [expressionKey(0)]: 7, [expressionKey(1)]: null },
    { t: 2, [expressionKey(0)]: 5, [expressionKey(1)]: 2 },
  ]);
  assert.deepEqual(undefinedStretches(plotted, 2), [[0, 2]]);
});

test('a Channel missing from a Sample keeps its last value, as rbed holds it', () => {
  const r = rows({ humidity: [null, 50, null, null, 60, null] });
  assert.equal(hasGaps(r, 'humidity'), true);
  assert.deepEqual(
    heldRows(r, 'humidity').map((x) => x.humidity),
    [null, 50, 50, 50, 60, 60],
  );
  assert.equal(hasGaps(rows({ temp: [1, 2] }), 'temp'), false);
});

test('few Samples at whole units are the time ticks; many are left to the chart', () => {
  assert.deepEqual(timeTicks(rows({ temp: [70, 72, 85] })), [0, 1, 2]);
  assert.equal(timeTicks(rows({ temp: Array(20).fill(70) })), undefined);
});

test('a within(...) window runs from the end of each Event of its Rule', () => {
  const lifecycle = {
    events: [{ name: 'DoorOpen', start: 0, end: 60_000 }, { name: 'Other', start: 0, end: 1000 }],
    discarded: [],
  };
  assert.deepEqual(windowStretches([{ after: 'DoorOpen', millis: 30 * 60_000 }], lifecycle, 60_000), [[1, 31]]);
});
