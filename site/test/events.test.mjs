// How the site orders and writes a Scenario's Events.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { aggregatesText, byStart, forDisplay } from '../src/events.ts';

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
