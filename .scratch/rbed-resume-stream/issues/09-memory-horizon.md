# 09: Memory Horizon

**What to build:** `detector.memoryHorizon()` returns a small immutable value with the longest time window in milliseconds and the largest Sample count that the Detector's Rules and watched Expressions use by configuration (`forDuration`, End Condition durations, `averageOverDuration`, `within(...)`, `maxGap`, `forSamples`, `averageOverSamples`, and every other windowed construct). It is computed once in `build()`. Its Javadoc says plainly that it is a configuration bound: Events in progress, Rules waiting for `when` to stop holding and `latest(...)` can reach further back, for which the Replay Point is the exact answer. See spec: Implementation Decisions "Memory Horizon API".

**Blocked by:** None (can start immediately; builds on rbed 0.3.0)

**Status:** done

- [x] `memoryHorizon()` exists with the value type and Javadoc as described (the glossary term is added by ticket 03, or here if 03 is not merged yet)
- [x] Tests pin the millis and Sample count for Rule sets mixing every windowed construct, including constructs inside watched Expressions and nested Expressions
- [x] A Detector without windowed constructs reports zero for both
- [x] `mvn -q verify` passes

## Comments

- `MemoryHorizon(long millis, int samples)` is a public record; `Detector.memoryHorizon()` returns it. Windowed constructs report while binding via package-private `Detector.windowMillis/windowSamples` (RuleState: phases and `maxGap`; Window constructors; `After.bind`; `EventCount.bind`). `Long.MAX_VALUE` (unbounded `maxGap`/`after`) counts for nothing.
- Snapshot restore (later tickets): the horizon is configuration, so a restored Detector gets it from binding; nothing to serialize.
- Glossary term "Memory Horizon" added to CONTEXT.md here.
