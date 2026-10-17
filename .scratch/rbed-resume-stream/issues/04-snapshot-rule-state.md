# 04: Snapshot: everything that defines a Rule's state

**What to build:** Snapshots carry every piece of a Rule's own state, so a restore is exact for Rules with durations (`forDuration`, `forSamples`), End Conditions and their durations, the knowledge whether `when` has been seen not holding (the restart rule), `maxGap` and data gaps, time advances, Pulse Channels, a closed Detector, and the Sample index of sampling-rate mode. See spec: Implementation Decisions "Snapshot format" (state list), "The guarantee".

**Blocked by:** 03

**Status:** done

- [x] Twin runs (harness from 02) at every moment never differ for Rule sets covering: `forDuration`, `forSamples`, End Conditions with and without their own duration, `when` still holding when an Event Ends on its End Condition, a Pending Event at the Snapshot, a Confirmed Event at the Snapshot, `maxGap` with gaps around the limit, `advanceTo` before and after the Snapshot, Pulse Channels present and absent, `close()`, and sampling-rate mode (the restored Detector derives the same timestamps)
- [x] Teeth: for every piece of state this ticket adds, leaving it out or restoring it wrong fails a test (recorded in Comments)
- [x] `mvn -q verify` passes

## Comments

- No library change: ticket 03 already writes all of this state, and the new twin tests found no difference. `SnapshotRuleStateTest` (new class, hand-made inputs, a Snapshot at every moment via `Differential.compareRestoredAt`, each with a non-vacuity check) covers `forSamples`, `forDuration`, End Conditions with/without their own duration, `when` still holding after an End Condition ended the Event (restart rule), `seenFalse`/`truncatedStart` with `maxGap`, `maxGap` gaps at limit-1/limit/limit+1, `advanceTo` before and after the Snapshot, Pulse Channels present and absent, `close()` (twice, push and advance after), and sampling-rate mode (Sample index).
- Teeth: a script replaced each `field = r.xxx();` restore line of `RuleState` (armed, seenFalse, truncatedStart, confirmedAt, inEndPhase, lastValidMoment, hasEnded), `Progress` (since, count) and `Detector` (started, now, closed, lastSample, samples, latestHistoryEnd) by a bare read, one at a time, running only `SnapshotRuleStateTest`: all killed. Survivors of that class alone, which belong to other tickets (killed by the full suite per ticket 03): `Detector.retained`/`dropped`/`hasHistory` (retention, ticket 09) and `Timestamps.base` (count windows, ticket 06). The `state` ordinal line is not a `field = r.x()` pattern and was not mutated. On macOS the script needs `sed -i ''` and `[[:space:]]`; `touch` the source after each swap.
