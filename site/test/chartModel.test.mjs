import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  channelAxesOf,
  channelBands,
  expressionKey,
  expressionRows,
  hasGaps,
  heldChannels,
  heldRows,
  expressionLabel,
  keyOf,
  latestEvent,
  lineRows,
  readoutValue,
  ruleState,
  stateColor,
  thresholdLines,
  thresholdText,
  tickText,
  timeTicks,
  undefinedStretches,
  windowRows,
} from '../src/chartModel.ts';

const scenario = (channels, pulse = []) => ({ id: 's', channels, pulse, rules: ['R'], timeUnit: { name: 's', millis: 1000 } });
const rows = (columns) => columns[Object.keys(columns)[0]].map((_, i) => ({ t: i, ...Object.fromEntries(Object.entries(columns).map(([c, v]) => [c, v[i]])) }));

test('Channels of one scale share a band; one of a very different scale gets its own', () => {
  assert.deepEqual(channelBands(scenario(['hr', 'spo2']), rows({ hr: [80, 100], spo2: [97, 86] })), [['hr', 'spo2']]);
  assert.deepEqual(channelBands(scenario(['speed', 'rpm']), rows({ speed: [0, 20], rpm: [800, 0] })), [['speed'], ['rpm']]);
  assert.deepEqual(
    channelBands(scenario(['errors', 'cpu', 'speed']), rows({ errors: [3, 12], cpu: [50, 95], speed: [4, 9] })),
    [['errors', 'speed'], ['cpu']],
  );
});

test("a Channel whose span is under a third of its band's would-be span gets a band of its own; a constant one does not", () => {
  // spo2 moves 11 within hr's 40: in one band its drop would be a few pixels.
  assert.deepEqual(channelBands(scenario(['hr', 'spo2']), rows({ hr: [80, 120], spo2: [97, 86] })), [['hr'], ['spo2']]);
  assert.deepEqual(channelBands(scenario(['spo2', 'hr']), rows({ hr: [80, 120], spo2: [97, 86] })), [['spo2'], ['hr']]);
  assert.deepEqual(channelBands(scenario(['weight', 'ratedLoad']), rows({ weight: [0, 6000], ratedLoad: [5000, 5000] })), [['weight', 'ratedLoad']]);
});

test('a band whose Channels are all hidden is left out, but the first', () => {
  const r = rows({ temp: [5, 10], door: [0, 1] });
  const model = (channels) => ({ scenario: scenario(['temp', 'door']), rows: r, rules: ['R'], channels, expressions: [], expressionRows: [], thresholds: [] });
  assert.deepEqual(channelAxesOf(model(['temp', 'door'])).map((a) => a.channels), [['temp'], ['door']]);
  assert.deepEqual(channelAxesOf(model(['temp'])).map((a) => a.channels), [['temp']]);
  assert.deepEqual(channelAxesOf(model(['door'])).map((a) => a.channels), [['temp'], ['door']]);
});

test('a band of a Channel that is only ever 0 or 1 has the ticks 0 and 1', () => {
  const model = { scenario: scenario(['serviceMode']), rows: rows({ serviceMode: [0, 1, 0] }), rules: ['R'], channels: ['serviceMode'], expressions: [], expressionRows: [], thresholds: [] };
  assert.deepEqual(channelAxesOf(model)[0].ticks, [0, 1]);
  model.thresholds = [{ channel: 'serviceMode', rule: 'R', condition: 'when >', value: 0.5 }];
  assert.deepEqual(channelAxesOf(model)[0].ticks, [0, 1]);
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
    channels: ['speed', 'rpm'],
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

test('the time a within(...) counts lies in the lane of the Rule that counts it, overlapping windows merged', () => {
  const lifecycle = {
    events: [
      { name: 'Sprint', start: 0, end: 60_000 },
      { name: 'Sprint', start: 90_000, end: 120_000 },
      { name: 'Other', start: 0, end: 1000 },
    ],
    discarded: [],
  };
  assert.deepEqual(
    windowRows([{ rule: 'Recovered', after: 'Sprint', millis: 120_000 }], lifecycle, 60_000),
    new Map([
      [
        'Recovered',
        [
          { t: 1, s: 0 },
          { t: 4, s: null },
        ],
      ],
    ]),
  );
});

test('a restart without History cuts the within(...) strips there: the new Detector forgets the earlier Events', () => {
  const lifecycle = { events: [{ name: 'Maintenance', start: 10, end: 11 }], discarded: [] };
  const windows = [{ rule: 'OverdueService', after: 'Maintenance', millis: 90 }];
  assert.deepEqual(windowRows(windows, lifecycle, 1).get('OverdueService'), [
    { t: 11, s: 0 },
    { t: 101, s: null },
  ]);
  assert.deepEqual(windowRows(windows, lifecycle, 1, 60).get('OverdueService'), [
    { t: 11, s: 0 },
    { t: 60, s: null },
  ]);
  // An Event after the restart opens its window in full.
  const later = { events: [{ name: 'Maintenance', start: 70, end: 71 }], discarded: [] };
  assert.deepEqual(windowRows(windows, later, 1, 60).get('OverdueService'), [
    { t: 71, s: 0 },
    { t: 161, s: null },
  ]);
});

test("what a Rule is at a time: Pending, Confirmed, Ended, Discarded or no Event, and the readout chip's color", () => {
  const lifecycle = { events: [{ name: 'R', start: 2000, confirmed: 4000, end: 6000 }], discarded: [{ name: 'R', start: 8000, discarded: 9000 }] };
  assert.deepEqual(
    [1, 2, 3, 4, 5, 6, 7, 8, 9].map((t) => ruleState(lifecycle, t, 1000)),
    ['no Event', 'Pending', 'Pending', 'Confirmed', 'Confirmed', 'Ended', 'no Event', 'Pending', 'Discarded'],
  );
  assert.equal(stateColor('Pending'), 'var(--event-pending)');
  assert.equal(stateColor('Confirmed'), 'var(--event-confirmed)');
  assert.equal(stateColor('Ended'), 'var(--event-confirmed)');
  assert.equal(stateColor('no Event'), 'transparent');
});

test('latest(...) reads nothing before the first Confirmed Event, then the running one, then the Ended one until the next is Confirmed', () => {
  const lift = (start, end) => ({ name: 'Lift', start: start * 1000, confirmed: start * 1000, end: end * 1000 });
  const events = [lift(10, 40), lift(60, 90)];
  assert.equal(latestEvent(events, 5, 1000), undefined);
  assert.equal(latestEvent(events, 20, 1000), events[0]);
  assert.equal(latestEvent(events, 50, 1000), events[0]);
  assert.equal(latestEvent(events, 60, 1000), events[1]);
  assert.equal(latestEvent(events, 120, 1000), events[1]);
});

test('a Pulse Channel reads "–" where a Sample does not carry it; another Channel keeps its last value', () => {
  const s = scenario(['errors', 'deploy'], ['deploy']);
  const r = rows({ errors: [3, null, 5], deploy: [1, null, null] });
  assert.equal(readoutValue(s, r, 'deploy', 0), '1');
  assert.equal(readoutValue(s, r, 'deploy', 1), '–');
  assert.equal(readoutValue(s, r, 'errors', 1), '3 (kept)');
  assert.equal(readoutValue(s, r, 'errors', 2), '5');
  // At a time advance, between Samples, every Channel holds its value, but a Pulse Channel.
  assert.equal(readoutValue(s, r, 'errors', 2.5), '5 (kept)');
  assert.equal(readoutValue(s, r, 'deploy', 0.5), '–');
  assert.equal(readoutValue(s, rows({ errors: [null, 2] }), 'errors', 0), '–');
});

test('held lines are drawn for Channels some Samples lack, and for all where time was advanced; never for a Pulse Channel', () => {
  const s = scenario(['errors', 'deploy', 'cpu'], ['deploy']);
  const r = rows({ errors: [3, null], deploy: [1, null], cpu: [50, 60] });
  assert.deepEqual(heldChannels(s, r, []), ['errors']);
  assert.deepEqual(heldChannels(s, r, [5]), ['errors', 'cpu']);
});

test('across a silence the line breaks, and the held line runs from the Sample before it through the advances', () => {
  const r = [
    { t: 0, hr: 80 },
    { t: 1, hr: 128 },
    { t: 4, hr: 92 },
  ];
  assert.deepEqual(
    lineRows(r, [2, 3]).map((x) => x.hr),
    [80, 128, undefined, undefined, 92],
  );
  assert.deepEqual(
    heldRows(r, 'hr', [2, 3]).map((x) => [x.t, x.hr]),
    [
      [0, null],
      [1, 128],
      [2, 128],
      [3, 128],
      [4, null],
    ],
  );
});

test('a threshold names its Rule, Channel and value in words', () => {
  assert.equal(thresholdText({ channel: 'hr', rule: 'Tachycardia', condition: 'when >', value: 100 }), 'Tachycardia when hr > 100');
  assert.equal(thresholdText({ channel: 'spo2', rule: 'Desaturation', condition: 'endWhen ≥', value: 92 }), 'Desaturation endWhen spo2 ≥ 92');
  assert.equal(thresholdText({ channel: 'speed', rule: 'Trip, Idling', condition: 'when <', value: 0.5 }), 'Trip, Idling when speed < 0.5');
  // Compared with another Channel: the value is that Channel's, so it is named instead.
  assert.equal(thresholdText({ channel: 'weight', rule: 'Overload', condition: 'when > ratedLoad', value: 5000 }), 'Overload when weight > ratedLoad');
});

test('an Expression threshold reads like a Channel one: its comparison on the line, its Rule in the key', () => {
  const x = { name: 'count(run).within(60 min)', unit: 'Runs', threshold: 3, rule: 'FrequentStarts', condition: 'when >', values: [] };
  assert.equal(expressionLabel(x), '> 3');
  assert.equal(thresholdText({ channel: x.name, rule: x.rule, condition: x.condition, value: x.threshold }, x.unit), 'FrequentStarts when count(run).within(60 min) > 3 Runs');
});

test('no line is drawn for a threshold against a shown Channel: that Channel is the line', () => {
  const axes = [{ id: 'band0', channels: ['weight', 'ratedLoad'], ticks: [0, 6000], domain: [0, 1], middle: '50%' }];
  const thresholds = [
    { channel: 'weight', rule: 'LoadLifted', condition: 'when >', value: 500 },
    { channel: 'weight', rule: 'Overload', condition: 'when > ratedLoad', value: 5000 },
  ];
  assert.deepEqual(thresholdLines(thresholds, axes, ['weight', 'ratedLoad']).map((l) => l.value), [500]);
  assert.deepEqual(thresholdLines(thresholds, axes, ['weight']).map((l) => l.value), [500, 5000]);
});

test('thresholds at one value share a line; a line close to a labelled one is left unlabelled; the Channel is named where an axis has several', () => {
  const axis = (id, channels, ticks) => ({ id, channels, ticks, domain: [0, 1], middle: '50%' });
  const axes = [axis('band0', ['hr', 'spo2'], [80, 90, 100, 110, 120]), axis('band1', ['load'], [0, 50, 100])];
  const t = (channel, condition, value) => ({ channel, rule: 'R', condition, value });
  const lines = thresholdLines(
    [t('hr', 'when >', 100), t('hr', 'endWhen <', 95), t('spo2', 'when <', 90), t('spo2', 'endWhen ≥', 91), t('load', 'when >', 80), t('load', 'endWhen <', 80)],
    axes,
    ['hr', 'spo2', 'load'],
  );
  assert.deepEqual(
    lines.map((l) => [l.axis, l.value, l.label, l.thresholds.length]),
    [
      ['band0', 100, 'hr > 100', 1],
      ['band0', 95, 'hr < 95', 1],
      ['band0', 90, '', 1],
      ['band0', 91, 'spo2 ≥ 91', 1],
      ['band1', 80, '> 80, < 80', 2],
    ],
  );
  // A hidden Channel's thresholds are not drawn, and the other is then alone on its axis.
  assert.deepEqual(
    thresholdLines([t('hr', 'when >', 100), t('spo2', 'when <', 90)], axes, ['hr']).map((l) => l.label),
    ['> 100'],
  );
});

test('the key lists the lifecycle in order, and Pending only where something was Pending for a while', () => {
  const at = (start, confirmed, end) => ({ name: 'R', start, confirmed, end });
  const names = (l) => {
    const key = keyOf(l);
    return [...key.states.map((s) => s.label), ...key.marks.map((m) => m.name)];
  };
  assert.deepEqual(names({ events: [at(1, 1, 3)], discarded: [] }), ['Confirmed', 'Confirmed at', 'Ended at']);
  assert.deepEqual(names({ events: [at(1, 2, 3)], discarded: [{ name: 'R', start: 5, discarded: 6 }] }), [
    'Pending',
    'Confirmed',
    'Went Pending',
    'Confirmed at',
    'Ended at',
    'Discarded at',
  ]);
});

test("an axis' ticks keep their decimals", () => {
  assert.deepEqual([0, 0.5, 1, 1500].map(tickText), ['0', '0.5', '1', '1,500']);
});
