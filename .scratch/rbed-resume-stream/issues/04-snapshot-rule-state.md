# 04: Snapshot: everything that defines a Rule's state

**What to build:** Snapshots carry every piece of a Rule's own state, so a restore is exact for Rules with durations (`forDuration`, `forSamples`), End Conditions and their durations, the knowledge whether `when` has been seen not holding (the restart rule), `maxGap` and data gaps, time advances, Pulse Channels, a closed Detector, and the Sample index of sampling-rate mode. See spec: Implementation Decisions "Snapshot format" (state list), "The guarantee".

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] Twin runs (harness from 02) at every moment never differ for Rule sets covering: `forDuration`, `forSamples`, End Conditions with and without their own duration, `when` still holding when an Event Ends on its End Condition, a Pending Event at the Snapshot, a Confirmed Event at the Snapshot, `maxGap` with gaps around the limit, `advanceTo` before and after the Snapshot, Pulse Channels present and absent, `close()`, and sampling-rate mode (the restored Detector derives the same timestamps)
- [ ] Teeth: for every piece of state this ticket adds, leaving it out or restoring it wrong fails a test (recorded in Comments)
- [ ] `mvn -q verify` passes
