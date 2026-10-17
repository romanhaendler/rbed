# A stream resumes from a Snapshot, or from a Replay Point

An application that feeds a Detector from a message stream must be able to stop and continue as if it had never stopped: the same Events, at the same timestamps, with the same flags and Aggregates, each reported once. Replaying recent messages into a fresh Detector is only exact if the replay starts early enough, and the application cannot know how early. Three capabilities answer this together: the **Snapshot**, the **Replay Point** and the **Memory Horizon**.

## Snapshot and Replay Point, both

- **Snapshot: instant, exact, with a format to keep.** `detector.snapshot()` returns the complete state as a byte array; `Detector.builder()...restore(bytes).build()` continues from it without replaying anything. The price is a binary format that later versions must keep readable or reject deliberately.
- **Replay Point: no format, but a replay.** `detector.replayPoint()` (a later ticket) tells the earliest timestamp from which a replay into a fresh Detector, with History of the Events Ended before it, reproduces the state. Nothing is stored but a timestamp and the Ended Events, so there is nothing to migrate. It is the fallback when no usable Snapshot exists (none taken yet, Rules changed, Snapshot lost or of an unsupported format version), and it bounds the replay of the messages after the latest Snapshot.
- **Memory Horizon: a configuration bound.** It tells how far back the configuration looks (longest window, largest Sample count) so operators can size retention and the Snapshot interval. It is not a guarantee: an Event in progress can reach further back, which only the Replay Point accounts for.

## The guarantee

A Detector restored from a Snapshot is observationally indistinguishable from the one the Snapshot was taken from: for every input that follows, every callback (kind, order, the complete Event, every Aggregate bit for bit), `value(...)`, `retainedValues()`, `droppedValues()`, the warnings logged and every exception (type and message) are identical. There are no preconditions beyond those `build()` enforces. Any difference is a bug. This is why state is written as raw bits (NaN, -0.0 and the compensated sums of moving averages included) and why the tests compare twins at every moment.

## The format

All written with the JDK only:

1. the magic bytes `RBED` and the **format version** (an int, currently 1);
2. a **fingerprint**: a 128-bit hash (two FNV-1a style lanes, hand-written because the compiled browser module has no `MessageDigest`; it guards against a mismatched configuration, not an adversary) over the Rules' readable descriptions (`Rule.toString()`, in the order added, plus the name and kind, Pulse or not, of every Channel) and, separately, over the watched Expressions' descriptions; then the sampling rate and the Retention Limit in plain. Separate parts let `build()` say which option differs;
3. the Detector's state: the latest moment and the Sample index, the closed flag, the retained and dropped counters, History's latest end, every Channel's track (values, previous value, times, absence), every moving-average window with its compensated sum, and per Rule its state machine, its Aggregates, and the Event state other Rules read (counts, durations, confirmation times kept for `count(...).within(...)`);
4. a CRC-32 of everything before it, so a corrupted or truncated array is rejected rather than half read.

Rings are written oldest first without their position or capacity, so a Snapshot of a restored Detector equals the Snapshot it was restored from byte for byte. The listener is not part of a Snapshot. The size is bounded by the Retention Limit, as the windows are.

`build()` throws `IllegalArgumentException` for an array that is not a Snapshot, is corrupted or truncated, has another format version, was taken with other Rules (also renamed) or other options, or is combined with `history(...)` (a Snapshot already contains what History would add). `snapshot()` throws `IllegalStateException` inside a listener callback, where a moment is half processed.

## Compatibility policy

A Snapshot is readable by any rbed version with the same format version. The format version changes only when the shape of the state changes; a release that changes it says so in its release notes, and rejects older Snapshots with a message pointing to the Replay Point. There is no promise of migrating across format versions: the Replay Point is the way across.

## Alternatives considered

- **Replay only.** Rejected as the only answer: an Event in progress, a window or a `latest(...)` Aggregate reaches back a data-dependent distance, and a restart is slow when the stream is long.
- **Java serialization.** Rejected: it ties the format to class layout and is a known hazard on untrusted input; the explicit format is small and checked.
- **Restoring into different Rules.** Rejected by design: the fingerprint makes a deployment that changed a Rule fall back to a replay instead of continuing from an incompatible state.
