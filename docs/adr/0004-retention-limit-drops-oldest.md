# Growable windows have a Retention Limit; overflow drops the oldest entries

`averageOverDuration` windows grow with time x sampling rate, so a long window on a fast stream can exhaust the heap. Every such window now has a Retention Limit (`Detector.builder().retentionLimit(n)`, default 1,000,000 entries). When a full window gets a new value, its oldest entry is dropped exactly as if it had left the window by time, so the average stays the mean of the entries that remain. The first drop in a Detector logs one `WARNING` through `System.Logger` naming the Rule and the limit; `Detector.droppedValues()` counts all drops.

## Alternatives considered

- **Throw when a window is full.** Rejected: it stops a live stream because of a planning error. Degraded results plus a warning are better than no results.
- **No default limit.** Rejected: memory stays unplanned until someone thinks to configure it, and the first sign is an `OutOfMemoryError` in production.

## Consequences

- The worst case of a Detector is known from its Rules: the number of windows times the Retention Limit.
- Results of a Rule whose window overflows deviate from what the Rule says; the warning and `droppedValues()` make that visible.
- `System.Logger` is in `java.base`, so the library stays dependency-free.
