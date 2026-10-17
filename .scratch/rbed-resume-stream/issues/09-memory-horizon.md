# 09: Memory Horizon

**What to build:** `detector.memoryHorizon()` returns a small immutable value with the longest time window in milliseconds and the largest Sample count that the Detector's Rules and watched Expressions use by configuration (`forDuration`, End Condition durations, `averageOverDuration`, `within(...)`, `maxGap`, `forSamples`, `averageOverSamples`, and every other windowed construct). It is computed once in `build()`. Its Javadoc says plainly that it is a configuration bound: Events in progress, Rules waiting for `when` to stop holding and `latest(...)` can reach further back, for which the Replay Point is the exact answer. See spec: Implementation Decisions "Memory Horizon API".

**Blocked by:** None (can start immediately; builds on rbed 0.3.0)

**Status:** ready-for-agent

- [ ] `memoryHorizon()` exists with the value type and Javadoc as described (the glossary term is added by ticket 03, or here if 03 is not merged yet)
- [ ] Tests pin the millis and Sample count for Rule sets mixing every windowed construct, including constructs inside watched Expressions and nested Expressions
- [ ] A Detector without windowed constructs reports zero for both
- [ ] `mvn -q verify` passes
