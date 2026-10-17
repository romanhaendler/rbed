// Repeats every bridge call of the JVM tests (scenarios/target/parity.json, written by
// BridgeTest) against the compiled JavaScript and compares the parsed results. Numbers are
// compared as values, not as text, with a relative tolerance for the last bit.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import * as bridge from '../scenarios/target/js/rbed.js';

const cases = JSON.parse(
  readFileSync(new URL('../scenarios/target/parity.json', import.meta.url), 'utf8'),
);

function assertClose(actual, expected, path) {
  if (typeof expected === 'number' && typeof actual === 'number') {
    const tolerance = 1e-15 * Math.max(Math.abs(expected), Math.abs(actual));
    assert.ok(Math.abs(actual - expected) <= tolerance, `${path}: ${actual} != ${expected}`);
  } else if (expected !== null && typeof expected === 'object') {
    assert.equal(typeof actual, 'object', path);
    assert.notEqual(actual, null, path);
    assert.equal(Array.isArray(actual), Array.isArray(expected), path);
    assert.deepEqual(Object.keys(actual).sort(), Object.keys(expected).sort(), path);
    for (const key of Object.keys(expected)) {
      assertClose(actual[key], expected[key], `${path}.${key}`);
    }
  } else {
    assert.equal(actual, expected, path);
  }
}

test('the JVM tests made bridge calls to compare', () => {
  assert.ok(cases.length > 0);
});

for (const { function: name, arguments: args, result } of cases) {
  test(`${name}(${args.join(', ')}) equals the JVM`, () => {
    const actual = bridge[name](...args);
    assertClose(JSON.parse(actual), result, 'result');
  });
}

test('a Snapshot the compiled module wrote restores in the compiled module and continues identically', () => {
  for (const stop of [1, 20, 45, 80, 119]) {
    const parameters = JSON.stringify({ stop, restart: 3 });
    const own = bridge.run('resuming', parameters, '{}', '');
    const snapshot = JSON.parse(own).resume.snapshot;
    assert.ok(snapshot.length > 0);
    assert.equal(bridge.run('resuming', parameters, '{}', snapshot), own);
  }
});
