# 02: Differential test harness and seeded generator

**What to build:** The test foundation for the guarantee. A harness runs two Detectors side by side on the same input and, after every push, advance and close, compares everything observable: the full callback sequences, the complete Events (doubles compared bit for bit), `value(...)` of every watched Expression, `retainedValues()`, `droppedValues()`, logged warnings, and the exception (type and message) thrown for the same invalid input. A seeded generator (hand-written, no dependency) builds random Rule sets from every construct the library offers and random inputs (irregular timestamps, gaps, advances, missing and Pulse values, values at and around thresholds, warm-ups, small Retention Limits). A failure prints its seed and a shrunk input that reproduces as a plain JUnit case. Verifiable on its own: two fresh Detectors with the same Rules fed the same input never differ, over thousands of generated cases. See spec: Testing Decisions "Differential testing of the guarantee".

**Blocked by:** None (can start immediately; builds on rbed 0.3.0)

**Status:** ready-for-agent

- [ ] The harness compares every observable listed above after every moment and reports the first difference with its moment and a readable diff
- [ ] Doubles are compared bit for bit (NaN, −0.0 and +0.0 distinguished)
- [ ] The generator covers every Condition and Expression, durations, End Conditions, `maxGap`, Rule references, Pulse Channels, watched Expressions, History at the first build, sampling-rate mode, and Retention Limits small enough to drop entries
- [ ] Inputs include irregular timestamps, gaps longer and shorter than `maxGap`, advances, missing and Pulse values, values exactly at thresholds, and invalid inputs (non-increasing timestamp, non-finite value, push after close)
- [ ] A failing case prints its seed and a shrunk input; a documented one-liner turns it into a regression test
- [ ] The regular build runs thousands of cases with fixed seeds within a reasonable time; a Maven profile runs far more with fresh seeds
- [ ] Self-check: two fresh Detectors never differ over all generated cases; a deliberately different Rule in one of them is reported as a difference
- [ ] `mvn -q verify` passes
