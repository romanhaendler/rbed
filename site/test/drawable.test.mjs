// What drawing relies on: a Scenario whose page lets the reader draw (<!-- scenario <id> draw -->)
// has the same Samples, in number, for every parameter value, so values drawn at one value still
// fit after a control moves. Checked against the compiled module at each control's ends and at
// each option, with every drawable Channel drawn.
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import * as bridge from '../scenarios/target/js/rbed.js';

const content = new URL('../src/content/', import.meta.url);
const drawable = readdirSync(content)
  .filter((f) => f.endsWith('.md'))
  .flatMap((f) => [...readFileSync(new URL(f, content), 'utf8').matchAll(/^<!-- scenario (\S+) draw(?: hide \S+)? -->$/gm)].map((m) => m[1]));
const catalogue = JSON.parse(bridge.scenarios());

test('pages let the reader draw', () => assert.ok(drawable.length > 0));

for (const id of drawable) {
  test(`${id} has as many Samples at every parameter value, so drawn values fit`, () => {
    const scenario = catalogue.find((s) => s.id === id);
    const run = (parameters, drawn = {}) => JSON.parse(bridge.run(id, JSON.stringify(parameters), JSON.stringify(drawn), ''));
    const count = run({}).samples.length;
    const drawn = Object.fromEntries(
      scenario.channels.filter((c) => !scenario.pulse.includes(c)).map((c) => [c, Array(count).fill(1)]),
    );
    for (const p of scenario.parameters) {
      const values = p.options ? p.options.map((_, i) => i) : [p.min, p.max];
      for (const value of values) {
        const result = run({ [p.id]: value }, drawn);
        if ('error' in result) {
          assert.equal(result.by, 'rbed', `${p.id}=${value}: ${result.error}`); // a lesson in rbed's errors
        } else {
          assert.equal(result.samples.length, count, `${p.id}=${value}`);
        }
      }
    }
  });
}
