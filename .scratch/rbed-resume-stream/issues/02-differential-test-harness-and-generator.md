# 02: Differential test harness and seeded generator

**What to build:** The test foundation for the guarantee. A harness runs two Detectors side by side on the same input and, after every push, advance and close, compares everything observable: the full callback sequences, the complete Events (doubles compared bit for bit), `value(...)` of every watched Expression, `retainedValues()`, `droppedValues()`, logged warnings, and the exception (type and message) thrown for the same invalid input. A seeded generator (hand-written, no dependency) builds random Rule sets from every construct the library offers and random inputs (irregular timestamps, gaps, advances, missing and Pulse values, values at and around thresholds, warm-ups, small Retention Limits). A failure prints its seed and a shrunk input that reproduces as a plain JUnit case. Verifiable on its own: two fresh Detectors with the same Rules fed the same input never differ, over thousands of generated cases. See spec: Testing Decisions "Differential testing of the guarantee".

**Blocked by:** None (can start immediately; builds on rbed 0.3.0)

**Status:** done

- [x] The harness compares every observable listed above after every moment and reports the first difference with its moment and a readable diff
- [x] Doubles are compared bit for bit (NaN, −0.0 and +0.0 distinguished)
- [x] The generator covers every Condition and Expression, durations, End Conditions, `maxGap`, Rule references, Pulse Channels, watched Expressions, History at the first build, sampling-rate mode, and Retention Limits small enough to drop entries
- [x] Inputs include irregular timestamps, gaps longer and shorter than `maxGap`, advances, missing and Pulse values, values exactly at thresholds, and invalid inputs (non-increasing timestamp, non-finite value, push after close)
- [x] A failing case prints its seed and a shrunk input; a documented one-liner turns it into a regression test
- [x] The regular build runs thousands of cases with fixed seeds within a reasonable time; a Maven profile runs far more with fresh seeds
- [x] Self-check: two fresh Detectors never differ over all generated cases; a deliberately different Rule in one of them is reported as a difference
- [x] `mvn -q verify` passes

## Comments

All in src/test/java/io/github/romanhaendler/rbed/ (public API only, no library change):

- `Differential`: the harness. `Differential.compare(config, watched, ops, sideA, sideB)` runs two Detectors on the same `Op` list (`Op.push/pushValues/advance/close`) and after the build and after every op compares: outcome (exception class and message), callback count and every callback (full Event, doubles as decimal plus raw bits, plus every watched Expression read inside the callback), warning count and text, `retainedValues`, `droppedValues`, `value(...)` of every watched Expression. First difference: moment, op, field, both values and everything each side observed. A `Side` is `(config, listener) -> Detector`; `Differential.FRESH` builds a fresh one. **Later tickets: compare a fresh Detector with a restored one by passing a Side that builds the restored twin** (and add `snapshot`/`replayPoint`/`memoryHorizon` lines to `Run.observe()`; `Run.apply` is where a per-moment snapshot-and-restore hook would go).
- `Scenario`: a Detector set-up plus input as plain data (`Node` trees for Conditions and Expressions, `RuleSpec`, `HistorySpec`, `Op`). `build()` gives the config and watched Expressions; `shrinks()` the smaller candidates; `toJava(name)` a JUnit method calling `Differential.assertFresh(config, watched, ops...)`.
- `ScenarioGenerator.generate(seed)`: seeded (`SplittableRandom`; `java.util.Random` correlates for small sequential seeds). Covers every Condition and Expression, `forSamples`/`forDuration`, End Conditions with their own durations, `maxGap`, Rule references (`during`, `after().unless().within()`, `count`, `totalDuration`, `latest`), Pulse Channels, watched Expressions, History, sampling-rate mode, Retention Limit 1..5, irregular timestamps, gaps around durations and `maxGap`, advances, missing and Pulse values, values at and one ulp around thresholds, -0.0, 1e300 (non-finite results), invalid input (non-increasing timestamp, NaN, infinity, push/advance after close, wrong mode).
- `DifferentialTest`: 3000 fixed-seed cases (about 1.5 s) with a non-vacuity check on the counts (pending, confirmed, ended, discarded, truncated flags, exceptions, warnings, defined watched values); coverage check of the generator; self-checks (extra or different Rule on one side is reported with its moment; -0.0 versus 0.0 reported; differing exception reported; failure message carries seed, diff and a shrunk JUnit method).
- Run: `mvn test -Pdifferential -Dtest=DifferentialTest` (200000 cases, fresh seeds printed; about 45 s; 0 differences). Reproduce: `-Drbed.diff.seed=<seed>`. Regression one-liner: paste the printed `@Test` method (it calls `Differential.assertFresh(...)`) into DifferentialTest.
- Finding: the 0.3.0 library shows no difference between two fresh Detectors in 200000 cases.
