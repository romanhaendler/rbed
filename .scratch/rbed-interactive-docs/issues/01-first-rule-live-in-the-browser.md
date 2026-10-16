# 01: First Rule live in the browser (TeaVM tracer bullet)

**What to build:** The README's first Rule (`HighTemperature`, `temp.above(80)`) runs in a browser, compiled from Java with TeaVM, and a plain web page shows its Channel values, its Events and a slider for the threshold that re-runs the Detector on every change. This is the feasibility spike and the first tracer bullet through every layer: the Scenario module with the first Scenario, the bridge (`scenarios()` and `run(scenarioId, parameters, drawnChannels)`, JSON text across the boundary, written by a small hand-written writer), the TeaVM build, and the web app (React, Vite) that loads the compiled module. The page may be unstyled; the site shell comes in 03. See spec: Implementation Decisions "TeaVM, JavaScript target", "First step is a feasibility spike", "A Scenario", "The bridge", "The result of run".

If TeaVM cannot compile rbed unchanged, stop: record what failed and the fallback options in this ticket's Comments, set it to needs-info, and do not change the library.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] The library module and its jar are unchanged: no new dependency, no TeaVM annotations
- [x] The Scenario module builds against the rbed artifact of the same commit
- [x] `scenarios()` returns the first Scenario with its Channels, its threshold parameter (default, limits, step, unit) and its source template
- [x] `run` returns the Samples and each Event with name, start, end and Truncated flags; invalid parameters return rbed's error message instead of a result
- [x] JUnit on the JVM: the first Scenario at default parameters yields the README's Event (`HighTemperature` from 2000 to 3000), and a changed threshold yields the expected different Events
- [x] Node test against the compiled JavaScript: for the same inputs, results equal the JVM's, comparing numbers as parsed values
- [x] The web page shows the Channel values, the Events, and a threshold slider; moving it updates the Events without a page reload
- [x] The size of the compiled module (raw and gzipped) is recorded in this ticket's Comments
- [x] ADR-0005 records that the documentation site runs rbed in the browser through TeaVM and that the library takes no site-specific code

## Comments

**Done.** TeaVM 0.16.0 compiles rbed 0.2.0 unchanged (no errors, no warnings). The built page works in headless Chromium: the slider at 70 gives `HighTemperature 1000..3000` truncatedStart, at 88 no Event, and there are no page errors.

**Size of the compiled module** `site/scenarios/target/js/rbed.js` (minified, `ADVANCED`): 109,931 bytes raw, 41,553 gzip -9, 32,224 brotli. The Vite bundle with React is 322.89 KB, 108.59 KB gzipped.

**Layout:**
- `site/` is the web app (React 19, Vite 8, TypeScript 7, npm, committed `package-lock.json`). `src/main.tsx` is the unstyled page, `src/bridge.ts` types the two bridge functions, and `vite.config.ts` aliases `rbed-scenarios` to the compiled module (typed in `src/rbed-scenarios.d.ts`).
- `site/scenarios/` is the Scenario module (own `pom.xml`, artifactId `rbed-site-scenarios`, package `io.github.romanhaendler.rbed.site`). `Bridge` is the only class with `@JSExport` and is TeaVM's main class. `Json` reads and writes JSON, `Scenario` is the base class with `Parameter`, and `FirstRule` is the first Scenario (id `first-rule`). A new Scenario is added to `Bridge.SCENARIOS`.
- `site/test/parity.test.mjs` is the Node parity test.

**Commands** (in `site/`, documented in `site/README.md`): `npm ci`, then `npm run build` (installs rbed from the repository root with `mvn install -DskipTests`, packages the Scenario module with TeaVM, type-checks, and runs `vite build` to `site/dist`) and `npm test` (installs rbed, runs `mvn verify` of the Scenario module, then `node --test`). `npm run preview` serves `dist/`.

**For later tickets:**
- The Scenario module's rbed version is the property `rbed.version`. The npm scripts pass the root pom's version (`npm run -s rbed:version`), so the site always builds against the rbed of the same commit. Running Maven in `site/scenarios` by hand uses the default in its pom.
- Parity cases are not maintained by hand: every call `BridgeTest` makes through its `run(...)` and `scenarios()` helpers is written with its JVM result to `site/scenarios/target/parity.json`, and the Node test repeats each call against the compiled module (numbers with a relative tolerance of 1e-15). New JUnit tests that call the bridge through those helpers get parity coverage for free.
- Result of `run`: `{"samples":[{"timestamp":..,"values":{"temp":..}}],"events":[{"name","start","confirmed","end","truncatedStart","truncatedEnd"}]}` or `{"error": message}`. Not included yet (spec items for later tickets): Aggregates, Discarded Pending Events (needs the listener), Expression values, Pulse flags of Channels. `drawnChannels` is accepted and ignored until ticket 11.
- Parameters: missing ids take their default, JSON `null` means NaN (so a non-finite value reaches rbed, which returns its message, e.g. `Limit is NaN`), and a non-number is an error.
- Catalogue (`scenarios()`): `id`, `title`, `timeUnit {name, millis}`, `channels` (names), `parameters [{id, label, default, min, max, step, unit}]`, and `source` with `${id}` placeholders (replaced in `bridge.ts`).
- No Vite `base` is set yet; GitHub Pages under `/rbed/` needs one (ticket 02).
- The browser check used Playwright from a scratch directory; `site/` has no Playwright dependency yet (the spec's smoke test comes later).
