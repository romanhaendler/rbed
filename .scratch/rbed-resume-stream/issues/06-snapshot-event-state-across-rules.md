# 06: Snapshot: Event state across Rules

**What to build:** Snapshots carry the Event state other Rules read: the latest Event of every Rule with its Aggregates (`latest(...)`), the timestamps `count(...).within(...)` keeps, `totalDuration`, the state behind `after(...).within(...)`, `during` and `unless`, and the History remembered at the first build. See spec: Implementation Decisions "Snapshot format" (state list), "The guarantee".

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] Twin runs at every moment never differ for Rule sets covering: `during`, `after(...).within(...)` with the window open and closed at the Snapshot, `unless`, `count(...).within(...)` near its limit and with timestamps leaving the window, `totalDuration`, `latest(...).duration()`, `latest(...).min/max/mean` including Channels the referenced Rule aggregates only because of `latest`, a self-reference, and History supplied at the first build (before the Snapshot)
- [ ] Teeth: for every piece of state this ticket adds, leaving it out or restoring it wrong fails a test (recorded in Comments)
- [ ] `mvn -q verify` passes
