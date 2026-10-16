# Spec: bounded and observable retention

Status: done

## Problem Statement

A Detector is meant to run indefinitely on a live stream, and the README promises that "memory stays bounded". An investigation of what a Detector actually stores shows that this only holds for part of its state:

- **Bounded by the Rules:** current values of the Channels Rules refer to, Aggregates (fixed per aggregated Channel), `averageOverSamples(n)` (exactly n values), duration progress and per-Rule Event state.
- **Bounded only by time x sampling rate:** `averageOverDuration(window)` keeps every value inside the window. With millisecond timestamps that is up to window-in-ms + 1 entries: a 7-day window at 1 kHz is about 600 million entries, roughly 10 GB. `count(rule).within(window)` keeps one timestamp per Confirmed Event inside the window. Both ring buffers grow by doubling and never shrink.
- **Unbounded:** every pushed Channel gets per-Detector state, including Channels no Rule uses. A stream whose Channel names keep changing (per-device or per-session names, a misconfigured exporter) grows the Detector forever. This contradicts ADR-0003 and the README, which say values of unused Channels are ignored.

A user has no way to see how much a Detector holds, no default ceiling, and no signal when a window grows beyond what they planned. The first sign is an `OutOfMemoryError` in production.

## Solution

- Values of Channels that no Rule uses are really ignored: they leave no trace in the Detector.
- Every growable window (`averageOverDuration`, `count(...).within(...)`) has a **Retention Limit**: a fixed maximum number of entries. The default is 1,000,000 entries per window. When a window would exceed it, its oldest entries are dropped, and the Detector logs a single warning that names the Rule and the limit. Detection continues.
- Users who need larger windows raise the limit on purpose with `Detector.builder().retentionLimit(n)`.
- `averageOverSamples(n)` declares its size up front. `build()` rejects one whose n exceeds the Retention Limit instead of silently allocating it.
- The Detector reports how much it holds (`retainedValues()`) and how much it has dropped (`droppedValues()`), so applications can export both as metrics.

With this, the worst case of a Detector is known from its Rules: the number of windows times the Retention Limit, plus one entry per referenced Channel.

## User Stories

1. As a library user running a Detector on an endless stream, I want its memory to have a fixed upper bound by default, so that a long-running service cannot run out of memory because of rbed.
2. As a library user, I want values of Channels no Rule uses to be ignored completely, so that a stream carrying ever-new Channel names does not grow the Detector.
3. As a library user feeding raw CSV or JSON rows, I want to push every column without filtering, so that I do not have to know which Channels the Rules use.
4. As a library user, I want `averageOverDuration` windows to stop growing at the Retention Limit, so that a long window on a fast stream cannot exhaust the heap.
5. As a library user, I want `count(rule).within(window)` to stop growing at the Retention Limit, so that a Rule firing very often within a long window cannot exhaust the heap.
6. As a library user, I want the oldest entries to be dropped when a window is full, so that the window keeps the most recent data, which matters most for live detection.
7. As a library user, I want detection to continue when entries are dropped, so that a misplanned window degrades results instead of stopping my stream.
8. As an operator, I want a warning in the log the first time a Detector drops entries, so that I learn that results deviate from what the Rules say.
9. As an operator, I want that warning only once per Detector, so that a full window on a 1 kHz stream does not flood the log.
10. As an operator, I want the warning to name the Rule whose window overflowed and the current Retention Limit, so that I know which Rule to fix or which limit to raise.
11. As an operator, I want the warning to go through the JDK's standard logging (`System.Logger`), so that it reaches my logging backend without rbed taking a dependency.
12. As a library user, I want to raise the Retention Limit with `Detector.builder().retentionLimit(n)`, so that I can deliberately allow larger windows when I have planned the memory.
13. As a library user, I want to lower the Retention Limit, so that I can give a Detector on a constrained device a tighter bound.
14. As a library user, I want `retentionLimit` to reject values below 1, so that a typo cannot disable every window.
15. As a library user, I want the default Retention Limit to be generous (1,000,000 entries, about 16 MB per window), so that typical windows (a minute or an hour at up to a few hundred Hz) never hit it.
16. As a library user, I want `build()` to reject `averageOverSamples(n)` with n above the Retention Limit, so that one declared window cannot allocate more than any other window may hold.
17. As a library user, I want that error to name the Rule and n, so that I can see which Rule to fix.
18. As an operator, I want `detector.retainedValues()`, the number of entries the Detector currently holds, so that I can export it as a gauge and see growth before it becomes a problem.
19. As an operator, I want `detector.droppedValues()`, the number of entries dropped because of the Retention Limit since the Detector started, so that I can alert on it instead of grepping logs.
20. As an operator, I want both numbers to be cheap to read from any moment between pushes, so that polling them from a metrics scraper costs nothing measurable.
21. As a library user, I want `retainedValues()` to count Channel values, window entries and count timestamps alike, so that one number tells me how much the Detector holds.
22. As a library user, I want `retainedValues()` to stay constant while I push Samples with new, unused Channel names, so that I can verify that unused Channels are ignored.
23. As a library user, I want `retainedValues()` to stay at most the Retention Limit per window however long I push, so that I can verify the bound.
24. As a library user, I want a dropped entry to leave a moving average consistent (the mean of the entries that remain), so that the average is still a correct average over a shorter span, not a corrupted value.
25. As a library user, I want `averageOverDuration` to keep its warm-up behaviour unchanged when entries are dropped, so that an average does not fall back to undefined in the middle of a stream.
26. As a library user, I want `count(...).within(...)` to count the Confirmed Events it still holds after a drop, so that the count is a lower bound rather than garbage.
27. As a library user, I want pushing a Sample to stay allocation-free in steady state, also when windows are full and drop entries, so that the performance guarantees of the README hold.
28. As a library user, I want nothing to change for Detectors whose windows stay below the limit, so that existing Rules produce the same Events as before.
29. As a library user restoring History, I want History Events counted by `count(...).within(...)` to obey the same limit, so that a huge History cannot bypass the bound.
30. As a library user, I want the README to state, per feature, how much it retains, so that I can plan memory from my Rules before deploying.
31. As a library user, I want the README's Performance section to describe the Retention Limit instead of claiming unconditional boundedness, so that the documentation matches the behaviour.
32. As a maintainer, I want the decision "drop oldest with one warning, configurable limit" recorded as an ADR, so that nobody later replaces it with failing or unbounded growth without seeing the trade-off.

## Implementation Decisions

- **Unused Channels:** applying a Sample only updates the state of Channels that were registered while binding Rules (referenced by a Condition, aggregated, or used by `latest`). Values of any other Channel are validated (a rejected Sample still leaves no trace) and then ignored. After `build()` the Detector's Channel map is never written again.
- **Retention Limit:** a per-Detector setting, `Detector.Builder.retentionLimit(int n)`, n >= 1, default 1,000,000. It applies to each growable window separately, not to the Detector's total, so the bound per window is predictable and the total is the number of windows times the limit. The two growable windows are the duration-based moving average and the confirmation timestamps behind `count(...).within(...)`. Their ring buffers never grow beyond the limit.
- **Overflow behaviour:** when adding an entry to a full window, the oldest entry is evicted first, exactly like an entry leaving the window by time: it is subtracted from the running compensated sum, and the periodic exact recomputation still applies. The warm-up state of a duration window depends on its first timestamp and is not affected.
- **Warning:** the first drop in a Detector logs one `WARNING` through `System.getLogger` (the logger is named after the package), stating the Rule whose window overflowed, the Retention Limit, and that `retentionLimit(...)` raises it. Later drops in the same Detector do not log. The Rule name is captured when the window is created during binding. `System.Logger` is in `java.base`, so the library stays dependency-free and the module descriptor gains no `requires`.
- **Fixed windows:** `averageOverSamples(n)` keeps its preallocated buffer. `build()` throws `IllegalArgumentException` if n exceeds the Retention Limit, naming the Rule and n.
- **Observability:** two new public methods on `Detector`:
  - `long retainedValues()`: the entries currently held, one per Channel state plus one per value in each moving-average window plus one per timestamp held for `count(...).within(...)`. Accumulators and other per-Rule state are fixed by the Rules and are not counted.
  - `long droppedValues()`: the entries evicted because of the Retention Limit since the Detector was built. Entries leaving a window by time are not drops.
  Both are plain reads of counters maintained on the hot path; they must not iterate over buffers.
- **Hot path:** maintaining the counters and the limit check must not allocate. The warning is the only allocation, once per Detector.
- **ADR-0004:** record "Growable windows have a configurable Retention Limit; overflow drops the oldest entries with a single warning instead of failing or growing without bound". The alternatives were throwing (it stops a live stream for a planning error) and no default limit (memory is unplanned until someone configures it).
- **Glossary:** add **Retention Limit** to CONTEXT.md: "The maximum number of entries a single window of a Detector keeps; beyond it, the oldest entries are dropped." Avoid: cap, max size, buffer limit.
- **Docs:** README gains a short "Memory" subsection near Performance with the per-feature retention table (Channel: 1 per referenced Channel; `averageOverSamples(n)`: n; `averageOverDuration(w)`: values in w, at most the Retention Limit; `count(...).within(w)`: Confirmed Events in w, at most the Retention Limit; everything else: constant per Rule) and the two monitoring methods. The sentence "Values of Channels that no Rule uses are ignored" becomes true and stays.

## Testing Decisions

- A good test pushes Samples through the public `Detector` API and asserts on reported Events, `retainedValues()` and `droppedValues()`. It does not look at ring buffers, array capacities or package-private classes.
- One new test class in the default suite, with small Retention Limits (e.g. 10) so it runs in milliseconds:
  - Unused Channels: pushing many Samples, each with a fresh unused Channel name, leaves `retainedValues()` unchanged.
  - A duration window on a dense stream stops at the limit: `retainedValues()` stays at the limit, `droppedValues()` counts the evictions, and the average equals the mean of the most recent limit-many values.
  - Below the limit nothing changes: same Events as without a limit, `droppedValues()` is 0.
  - `count(...).within(...)` with more Confirmed Events in the window than the limit counts at most the limit and reports drops; History beyond the limit is dropped too.
  - The warning is logged exactly once per Detector, names the Rule and the limit (captured with a `java.util.logging` handler on the package logger, which the default `System.Logger` backend routes to).
  - `retentionLimit(0)` is rejected; `averageOverSamples(n)` with n above the limit fails in `build()` with the Rule's name.
- The existing allocation checks in the performance suite get one more case: a duration window that is full and drops an entry with every Sample still allocates nothing per Sample.
- Prior art: `MovingAverageFunctionsTest` and `EventCountAndDurationTest` for window semantics through the public API, `ChannelTest` for Channel handling, `PerformanceTest` for allocation checks.

## Out of Scope

- A Detector-wide total limit across all windows; the per-window limit already makes the total computable from the Rules.
- Shrinking ring buffers after a burst; capacity is bounded by the limit, which is enough.
- Per-Rule or per-window limits; one setting per Detector until someone needs finer control.
- Pluggable warning handlers or an `EventListener` callback for drops; `droppedValues()` plus `System.Logger` cover monitoring.
- The list returned by batch `detect(...)`; it holds the result the caller asked for.
- Estimating bytes instead of entries.

## Further Notes

- The default of 1,000,000 entries means about 16 MB for a duration window (timestamp plus value) and 8 MB for count timestamps. It is never reached by a window shorter than about 16 minutes at 1 kHz or 4.6 hours at 60 Hz.
- With millisecond timestamps a duration window can never hold more than window-in-ms + 1 entries, so the limit only bites for windows longer than about 16.7 minutes, and only on streams dense enough to fill them.
- The leak from unused Channels was found by reading the code path that applies a Sample: it looks up or creates state for every pushed Channel. The README's Performance claim ("memory stays bounded however long an Event lasts") is true for Event length but not for window length or Channel names; the docs change fixes the wording.
