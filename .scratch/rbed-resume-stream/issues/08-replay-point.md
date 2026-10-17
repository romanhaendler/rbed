# 08: Replay Point

**What to build:** `detector.replayPoint()` returns the Replay Point as an `OptionalLong`: empty before the first Sample and in sampling-rate mode, otherwise the earliest timestamp from which replaying the same input into a fresh Detector, with History of the Events Ended before it, reproduces the Detector exactly. It covers every component the spec lists (Events in progress and End Condition durations, Rules that have not seen `when` stop holding, moving-average windows, the previous Sample behind `rate()`, `age()` and `maxGap`, `latest(...)` Aggregates and running Events) plus whatever the tests show is needed. Reading it does no work proportional to history and allocates nothing beyond the result. See spec: Implementation Decisions "Replay Point API", "The guarantee"; Testing Decisions.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] `replayPoint()` exists with Javadoc stating the definition, the guarantee and the preconditions
- [ ] For every stop point of representative Rule sets and of thousands of generated cases (harness and generator from 02), a replay from the stored Replay Point with History of the Events Ended before it, side effects suppressed up to the stop, never differs from the uninterrupted run from the stop on
- [ ] For each representative Rule set, replaying from one Sample after the Replay Point differs for at least one stop point (the Replay Point is not trivially early)
- [ ] Empty before the first Sample and in sampling-rate mode; no extra allocation per Sample (existing allocation checks)
- [ ] Any case the Replay Point cannot make exact with reasonable bookkeeping is recorded in ADR-0007 and the Javadoc as a precondition, with a test that documents it
- [ ] `mvn -q verify` passes
