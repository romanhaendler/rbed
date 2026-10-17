// How the site orders and writes a Scenario's Events.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { aggregatesText, byStart, differences, forDisplay } from '../src/events.ts';

const event = (name, start, end, aggregates = {}) => ({
  name, start, confirmed: start, end, truncatedStart: false, truncatedEnd: false, aggregates,
});

test('Events in the order they Ended are shown in the order they started, then by name', () => {
  const ended = [event('Overheat', 121, 331), event('HighLoad', 100, 400), event('Desaturation', 121, 200)];
  assert.deepEqual(
    byStart(ended).map((e) => `${e.name} ${e.start}`),
    ['HighLoad 100', 'Desaturation 121', 'Overheat 121'],
  );
  assert.equal(ended[0].name, 'Overheat', 'the bridge result keeps rbed’s order');
  assert.deepEqual(forDisplay({ events: ended, discarded: [] }).events, byStart(ended));
  assert.deepEqual(forDisplay({ error: 'no' }), { error: 'no' });
});

test('an Event whose Rule reads no Channel says so instead of leaving its Aggregates empty', () => {
  assert.equal(aggregatesText(event('FrequentStarts', 1, 2)), 'none (the Rule reads no Channel)');
  const a = { min: 1, max: 3, mean: 2, count: 2, timeWeightedMean: 2, integral: 4 };
  assert.match(aggregatesText(event('Run', 1, 2, { power: a })), /^power/);
});

test('a resumed run differs from the uninterrupted one by the Events it adds and the ones it lacks', () => {
  const a = { min: 1, max: 3, mean: 2, count: 2, timeWeightedMean: 2, integral: 4 };
  const whole = [event('Overheat', 10, 20, { temp: a }), event('Overheat', 40, 60), event('Repeated', 43, 74)];
  const truncated = { ...event('Overheat', 45, 60), truncatedStart: true };
  const resumed = [whole[0], { ...whole[1], note: 'a note does not count' }, truncated];
  const { extra, missing } = differences(resumed, whole);
  assert.deepEqual(extra, [truncated]);
  assert.deepEqual(missing, [whole[2]]);
  assert.deepEqual(differences(whole, [...whole]), { extra: [], missing: [] });
  assert.equal(differences([event('Overheat', 10, 20, { temp: { ...a, mean: 2.5 } })], [whole[0]]).extra.length, 1, 'an Aggregate counts');
});
