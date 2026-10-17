# 05: Snapshot: windows and derived values

**What to build:** Snapshots carry every derived value, bit for bit: moving averages over Samples and over a duration (including their compensated sums and ring buffers), `rate()`, `age()`, integrals and every Aggregate of an Event in progress (Sample-based and time-weighted), the retention counters (`retainedValues()`, `droppedValues()`) and the warning state of the Retention Limit, and watched Expressions. See spec: Implementation Decisions "Snapshot format" (state list), "The guarantee".

**Blocked by:** 03

**Status:** done

- [x] Twin runs at every moment never differ for Rule sets covering: `averageOverSamples` during and after warm-up, `averageOverDuration` with Samples leaving the window, a window that reaches the Retention Limit and drops entries, `rate()`, `age()` across advances, arithmetic Expressions, Aggregates of a running Event (min, max, mean, count, time-weighted mean, integral), and watched Expressions of every kind
- [x] Compensated sums are restored exactly: long runs with values chosen to make naive summation drift give bit-identical results after a restore
- [x] A Retention Limit warning logged before the Snapshot is not logged again after the restore unless the original would log it
- [x] Teeth: for every piece of state this ticket adds, leaving it out or restoring it wrong fails a test (recorded in Comments)
- [x] `mvn -q verify` passes

## Comments

- No main-code change was needed: ticket 03 already writes this state and every new test passed at once. New class `SnapshotWindowsTest` (twin at every moment): `averageOverSamples` (warm-up and after), `averageOverDuration` with Samples leaving, rate, age, arithmetic, Aggregates of running Events (Sample-based and time-weighted via `describe`), count/totalDuration, 12 noisy seeds; a Retention Limit window that drops (exactly one warning per run, none after a restore past it, one after a restore before it); compensated sums with 1e16-scale values over 220 Samples; a Pulse Channel absent from the latest Sample.
- Teeth: each restore assignment in `ValueTrack`, `Window` (both kinds), `Accumulator`, `CompensatedSum`, `ChannelState` and `Detector.retained/dropped` was replaced by a bare read, one at a time, running only `SnapshotWindowsTest`; also the OverSamples `System.arraycopy`. 31 of 31 killed. The first run left `ChannelState.absent` alive (no pulse in the test); the pulse test was added and kills it.
