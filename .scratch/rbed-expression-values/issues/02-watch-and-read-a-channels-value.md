# 02: Watch and read a Channel's value (tracer bullet)

**What to build:** A user can register Expressions with `Detector.builder().watch(...)` and read their current value with `detector.value(expression)`, an `OptionalDouble` that is empty while the value is undefined. This ticket makes it work end to end for Channels and Expressions derived from a Channel (moving averages over Samples and over a duration, `rate()`, `age()`, arithmetic between Channels), documented in the README and recorded in ADR-0006, with the version raised to 0.3.0. See spec: Solution 3; Implementation Decisions "New public API", "Binding", "No extra evaluation step", "ADR-0006", "README", "Version".

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `Builder.watch(Expression...)` exists; calling it more than once and watching the same Expression twice is harmless
- [ ] `Detector.value(Expression)` returns the value at the latest moment processed, or empty while undefined (before the first Sample, during a moving average's warm-up)
- [ ] A Channel is matched by name, every other Expression by identity; reading an Expression that was not watched throws `IllegalArgumentException` naming it
- [ ] A watched Expression that no Rule uses works, and its Channels count as used (`retainedValues()` reflects them)
- [ ] Watching changes no Event: an existing Scenario yields the same Events with and without watched Expressions
- [ ] Pushing a Sample does no extra work and allocates nothing more with watched Expressions (existing allocation checks pass with watching enabled)
- [ ] JUnit tests through the public API cover a Channel, a moving average during and after warm-up, `rate()`, `age()` and an arithmetic Expression
- [ ] ADR-0006 records the pull-style, registered read, the fail-fast unwatched read and empty-for-undefined
- [ ] README: a subsection "Reading an Expression's value" in "Conditions and Expressions" with a short example; table of contents updated if needed
- [ ] The pom version is 0.3.0 (no release)
- [ ] `mvn -q verify` passes
