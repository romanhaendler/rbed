# Growable windows have a Retention Limit; overflow drops the oldest entries

`averageOverDuration` windows grow with time x sampling rate, and the timestamps kept for `count(...).within(...)` grow with the Event rate, so either can exhaust the heap. Every such window and timestamp buffer now has a Retention Limit (`Detector.builder().retentionLimit(n)`, default 1,000,000 entries). `averageOverSamples(n)` has a fixed size, so `build()` rejects a Rule whose `n` exceeds the limit. When a full window or buffer gets a new entry, its oldest entry is dropped exactly as if it had left the window by time, so the average stays the mean of the entries that remain. The first drop in a Detector logs one `WARNING` through `System.Logger` naming the Rule (or, for a timestamp buffer, `count of <Rule>`) and the limit; `Detector.droppedValues()` counts all drops.

## Alternatives considered

- **Throw when a window is full.** Rejected: it stops a live stream because of a planning error. Degraded results plus a warning are better than no results.
- **No default limit.** Rejected: memory stays unplanned until someone thinks to configure it, and the first sign is an `OutOfMemoryError` in production.

## Consequences

- The worst case of a Detector is known from its Rules: the number of windows times the Retention Limit.
- Results of a Rule whose window overflows deviate from what the Rule says; the warning and `droppedValues()` make that visible.
- `System.Logger` is in `java.base`, so the library stays dependency-free.
