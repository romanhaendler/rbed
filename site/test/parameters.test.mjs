// Parameter values in the page address: only those that differ from the default are written,
// and reading an address gives back exactly what was written. And the source shown for them.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fillSource, fromQuery, toQuery, usedParameters } from '../src/parameters.ts';

const PARAMETERS = [
  { id: 'threshold', default: 80, min: 60, max: 90, step: 0.5 },
  { id: 'duration', default: 30, min: 0, max: 60, step: 0.05 },
];

test('an address without parameters gives the defaults', () => {
  assert.deepEqual(fromQuery('', PARAMETERS), { threshold: 80, duration: 30 });
});

test('a parameter in the address overrides its default, unknown ones are ignored', () => {
  assert.deepEqual(fromQuery('?threshold=72.5&x=1', PARAMETERS), { threshold: 72.5, duration: 30 });
});

test('a value that is no finite number gives the default', () => {
  assert.equal(fromQuery('?threshold=abc', PARAMETERS).threshold, 80);
  assert.equal(fromQuery('?threshold=Infinity', PARAMETERS).threshold, 80);
  assert.equal(fromQuery('?threshold=', PARAMETERS).threshold, 80);
});

test("a value is kept to its control's range and steps, so the source shows what runs", () => {
  assert.equal(fromQuery('?threshold=1000', PARAMETERS).threshold, 90);
  assert.equal(fromQuery('?threshold=-5', PARAMETERS).threshold, 60);
  assert.equal(fromQuery('?threshold=72.3', PARAMETERS).threshold, 72.5);
  assert.equal(fromQuery('?threshold=72.2', PARAMETERS).threshold, 72);
  assert.equal(fromQuery('?duration=0.15000001', PARAMETERS).duration, 0.15, 'no binary noise');
  const choice = [{ id: 'mode', default: 0, min: 0, max: 2, step: 1, options: [{ source: 'a' }, { source: 'b' }, { source: 'c' }] }];
  assert.equal(fromQuery('?mode=1.4', choice).mode, 1);
  assert.equal(fromQuery('?mode=7', choice).mode, 2);
});

test('only values that differ from the default go into the address, other keys stay', () => {
  assert.equal(toQuery('?x=1&threshold=70', { threshold: 80, duration: 40 }, PARAMETERS), '?x=1&duration=40');
  assert.equal(toQuery('?threshold=70', { threshold: 80, duration: 30 }, PARAMETERS), '');
});

test('reading back what was written gives the same values', () => {
  const values = { threshold: 61, duration: 0.25 };
  assert.deepEqual(fromQuery(toQuery('', values, PARAMETERS), PARAMETERS), values);
});

const CHOICE = [
  {
    id: 'expression',
    default: 0,
    options: [{ source: 'temp.rate()' }, { source: 'temp.averageOverSamples(${samples})' }],
  },
  { id: 'samples', default: 5 },
  { id: 'window', default: 10 },
];

test('a choice stands for its option, whose placeholders are filled in too', () => {
  const template = 'when(${expression}.above(${threshold}))';
  const parameters = [...CHOICE, { id: 'threshold', default: 24 }];
  assert.equal(
    fillSource(template, parameters, { expression: 0, samples: 5, threshold: 24 }),
    'when(temp.rate().above(24))',
  );
  assert.equal(
    fillSource(template, parameters, { expression: 1, samples: 7, threshold: 24.5 }),
    'when(temp.averageOverSamples(7).above(24.5))',
  );
});

test('the parameters in use are every choice and those the source for the current values names', () => {
  const template = 'when(${expression})';
  assert.deepEqual(usedParameters(template, CHOICE, { expression: 0 }), ['expression']);
  assert.deepEqual(usedParameters(template, CHOICE, { expression: 1 }), ['expression', 'samples']);
});
