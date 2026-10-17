# 03: Snapshot and restore, tracer bullet

**What to build:** `detector.snapshot()` returns a versioned byte array and `Detector.builder()...restore(bytes).build()` returns a Detector in that state, end to end for a simple Rule set (Channel comparisons, a Rule without durations). The format (magic header, format version, fingerprint over the descriptions from ticket 01 and the options, then the state) is in place, and every documented rejection works. The twin harness from ticket 02 proves, for this Rule set and every moment of its input, that the restored Detector never differs from the original. ADR-0007 and the three glossary terms (Snapshot, Replay Point, Memory Horizon) are written. See spec: Implementation Decisions "The guarantee", "Glossary", "Snapshot API", "Snapshot format", "Compatibility policy", "ADR-0007".

**Blocked by:** 01, 02

**Status:** done

- [x] `snapshot()` and `Builder.restore(byte[])` exist with Javadoc stating the guarantee and the rejections
- [x] `build()` rejects, with clear `IllegalArgumentException` messages: a different Rule, a renamed Rule, a different option (sampling rate, Retention Limit, watched Expressions), an unsupported format version, a corrupted or truncated array, and `restore` together with `history`
- [x] `snapshot()` inside a listener callback throws `IllegalStateException`; taking a Snapshot does not change the Detector's later behaviour
- [x] For the tracer Rule set, a Snapshot at every moment (including before the first Sample and after `close()`) restores into a twin that never differs (harness from 02)
- [x] A Snapshot of a restored Detector is byte-for-byte identical to the one it was restored from
- [x] ADR-0007 and the CONTEXT.md terms are written; the README is not yet changed (ticket 10)
- [x] Teeth: for every piece of state this ticket writes, leaving it out or restoring it wrong fails a test (recorded in Comments)
- [x] `mvn -q verify` passes

## Comments

- Scope exceeded on purpose: instead of a guard for unsupported constructs, `snapshot()` writes the whole state of every construct (Channel tracks, moving-average windows with compensated sums, Rule state machines and Aggregates, Event state, count timestamps, counters, closed flag, Sample index). Tickets 04-06 are therefore mostly extra tests and teeth for their areas; the generated twin test already covers them.
- API: `Detector.snapshot()`, `Detector.Builder.restore(byte[])`. Format in `Snapshots` (header, fingerprint, CRC-32 trailer); state is written by `save(StateWriter)` / `load(StateReader)` methods next to the fields in `Accumulator`, `Progress`, `ValueTrack`, `ChannelState`, `Window`, `CompensatedSum`, `Timestamps`, `EventState`, `RuleState`, `Detector.saveState/loadState`. Doubles go as raw bits. Rings are written oldest first without position or capacity. `Timestamps.Counter.start` is not state (a cache that only moves forward).
- New state to add later (format version 1 stays until release): add it to the matching `save`/`load` pair; a Snapshot's byte-for-byte round trip is checked by the harness.
- Fingerprint: 128-bit hand-written hash (the TeaVM module has no `MessageDigest`; `java.util.zip.CRC32` compiles) over `Rule.toString()` in order plus `channel <name> [pulse]` lines, and separately over the watched Expressions' `toString()`; sampling rate and Retention Limit in plain. History is not in it (restore and history are exclusive).
- A Snapshot with a version other than 1 is rejected with a message that points to the Replay Point (ticket 08 adds `replayPoint()`).
- `processing` flag in `Detector` marks push/advance/close; `snapshot()` inside a callback throws. No allocation added on the push path (PerformanceTest passes).
- Harness: `Differential.compareRestoredAt(config, restoreConfig, watched, ops, k)` runs the original through `k` ops, restores a twin from a Snapshot there, compares retained/dropped/watched at once, the round-trip bytes, that taking a Snapshot left the original unchanged, then compares both to the end. `Scenario.Built.configWithoutHistory` is the Builder set-up without History (restore rejects it). `SnapshotTwinTest` runs it at every moment (also after `close()`, plus a closing `close()` and a push after it) over 400 generated cases (`-Drbed.twin.cases=N` for more, `-Drbed.diff.seed=S` for one) and the tracer Rule set. Ticket 07 scales it.
- Teeth: a script replaced each of the 56 `field = r.xxx();` restore lines (every field of every piece of state) by a bare read, one at a time: all 56 made `SnapshotTest`/`SnapshotTwinTest` fail, 0 survivors. Dropping the `System.arraycopy` of the OverSamples ring also fails (the round trip, at seed 1). Pieces written but not assigned from a bare read (the `load(r)` calls of nested objects, the writes) break the format if left out, so any omission fails `everyFlippedByteIsRejected`-style alignment or a twin. Caution when repeating this: restoring a mutated file by `mv` keeps an old mtime and Maven then keeps the mutated class; `touch` the sources.
- Contract tests in `SnapshotTest`: every rejection (Rule changed/renamed/added, Channel kind, sampling rate, Retention Limit, watched, version, not a Snapshot, every truncation length, every flipped byte, restore with history in both call orders), snapshot in callbacks, identical round trip, closed Detector, Snapshot leaves the Detector unchanged.
- The site builds and its tests pass (the Bridge does not call snapshot yet, but `Detector`'s constructor reaches `Snapshots`). Tickets 10/11 can use `snapshot()` from the Scenario.
- ADR-0007 is written with the Snapshot parts; the Replay Point's definition and preconditions are only named there; ticket 08 completes them. CONTEXT.md: Snapshot and Replay Point added (Memory Horizon was already there from ticket 09).
