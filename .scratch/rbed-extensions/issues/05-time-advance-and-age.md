# 05: Advancing time without Samples, and Channel age

**What to build:** `detector.advanceTo(t)` lets time pass without data: max-gap checks apply, durations and End-Condition durations can be reached, so Events can start, be Confirmed, Discarded or End while no Sample arrives. `channel("x").age()` gives the milliseconds since the Channel's last value, so "sensor silent for 10 s" is a Rule. An advance is not a Sample: it does not count for `forSamples`, Sample-based averages or Aggregates. See spec (1).

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `advanceTo` with strictly increasing timestamps; `IllegalStateException` in sampling-rate mode or after close
- [x] A SensorLost Rule on `age()` fires via `advanceTo` while no Samples arrive
- [x] `forDuration` confirmations and End-Condition durations complete on an advance; `maxGap` truncation fires on an advance
- [x] `forSamples` counts and Aggregates' counts are unaffected by advances
