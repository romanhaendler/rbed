# 06: Snapshot: Event state across Rules

**What to build:** Snapshots carry the Event state other Rules read: the latest Event of every Rule with its Aggregates (`latest(...)`), the timestamps `count(...).within(...)` keeps, `totalDuration`, the state behind `after(...).within(...)`, `during` and `unless`, and the History remembered at the first build. See spec: Implementation Decisions "Snapshot format" (state list), "The guarantee".

**Blocked by:** 03

**Status:** done

- [x] Twin runs at every moment never differ for Rule sets covering: `during`, `after(...).within(...)` with the window open and closed at the Snapshot, `unless`, `count(...).within(...)` near its limit and with timestamps leaving the window, `totalDuration`, `latest(...).duration()`, `latest(...).min/max/mean` including Channels the referenced Rule aggregates only because of `latest`, a self-reference, and History supplied at the first build (before the Snapshot)
- [x] Teeth: for every piece of state this ticket adds, leaving it out or restoring it wrong fails a test (recorded in Comments)
- [x] `mvn -q verify` passes

## Comments

- No library change: ticket 03 already writes all of this state; no difference found. New `EventStateSnapshotTest` (hand-written input, Snapshot at every moment incl. after `advance` and `close()`, asserts the run is not vacuous): during, after(...).within(...) with the window open and closed, after(...).unless(...), after/unless/within in an End Condition, count(...).within(...) for windows 20/40/75/200 (timestamps leave the window) and without a window, totalDuration, latest(...).duration/min/max/mean, latest of a Channel the referenced Rule aggregates only because of latest, a chain of Rules reading each other, History supplied at the first build (restore config has none).
- A self-reference cannot be tested as a twin: the Detector rejects it at build (SequenceTest, EventCountAndDurationTest), so the chain test stands in.
- Teeth: each of these restore lines was replaced by a bare read (or the target replaced by a throwaway), one at a time, running only `EventStateSnapshotTest`: EventState 9 fields (`confirmed`, `hasEnded`, `lastEnd`, `hasBeenConfirmed`, `lastConfirmed`, `count`, `lastDuration`, `runningStart`, `endedDuration`), `Timestamps` entries and `base`, `RuleState.latestEnded` (`latest` Aggregates), `RuleState.hasEnded`, `RuleState.confirmedAt`. 14 of 14 killed, 0 survivors. Remember to `touch` sources after restoring a mutated file.
