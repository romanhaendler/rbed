# 03: Snapshot and restore, tracer bullet

**What to build:** `detector.snapshot()` returns a versioned byte array and `Detector.builder()...restore(bytes).build()` returns a Detector in that state, end to end for a simple Rule set (Channel comparisons, a Rule without durations). The format (magic header, format version, fingerprint over the descriptions from ticket 01 and the options, then the state) is in place, and every documented rejection works. The twin harness from ticket 02 proves, for this Rule set and every moment of its input, that the restored Detector never differs from the original. ADR-0007 and the three glossary terms (Snapshot, Replay Point, Memory Horizon) are written. See spec: Implementation Decisions "The guarantee", "Glossary", "Snapshot API", "Snapshot format", "Compatibility policy", "ADR-0007".

**Blocked by:** 01, 02

**Status:** ready-for-agent

- [ ] `snapshot()` and `Builder.restore(byte[])` exist with Javadoc stating the guarantee and the rejections
- [ ] `build()` rejects, with clear `IllegalArgumentException` messages: a different Rule, a renamed Rule, a different option (sampling rate, Retention Limit, watched Expressions), an unsupported format version, a corrupted or truncated array, and `restore` together with `history`
- [ ] `snapshot()` inside a listener callback throws `IllegalStateException`; taking a Snapshot does not change the Detector's later behaviour
- [ ] For the tracer Rule set, a Snapshot at every moment (including before the first Sample and after `close()`) restores into a twin that never differs (harness from 02)
- [ ] A Snapshot of a restored Detector is byte-for-byte identical to the one it was restored from
- [ ] ADR-0007 and the CONTEXT.md terms are written; the README is not yet changed (ticket 10)
- [ ] Teeth: for every piece of state this ticket writes, leaving it out or restoring it wrong fails a test (recorded in Comments)
- [ ] `mvn -q verify` passes
