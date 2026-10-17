# 05: Snapshot: windows and derived values

**What to build:** Snapshots carry every derived value, bit for bit: moving averages over Samples and over a duration (including their compensated sums and ring buffers), `rate()`, `age()`, integrals and every Aggregate of an Event in progress (Sample-based and time-weighted), the retention counters (`retainedValues()`, `droppedValues()`) and the warning state of the Retention Limit, and watched Expressions. See spec: Implementation Decisions "Snapshot format" (state list), "The guarantee".

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] Twin runs at every moment never differ for Rule sets covering: `averageOverSamples` during and after warm-up, `averageOverDuration` with Samples leaving the window, a window that reaches the Retention Limit and drops entries, `rate()`, `age()` across advances, arithmetic Expressions, Aggregates of a running Event (min, max, mean, count, time-weighted mean, integral), and watched Expressions of every kind
- [ ] Compensated sums are restored exactly: long runs with values chosen to make naive summation drift give bit-identical results after a restore
- [ ] A Retention Limit warning logged before the Snapshot is not logged again after the restore unless the original would log it
- [ ] Teeth: for every piece of state this ticket adds, leaving it out or restoring it wrong fails a test (recorded in Comments)
- [ ] `mvn -q verify` passes
