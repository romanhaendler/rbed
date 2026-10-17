# A stream resumes from a Snapshot, or from a Replay Point

An application that feeds a Detector from a message stream must be able to stop and continue as if it had never stopped: the same Events, at the same timestamps, with the same flags and Aggregates, each reported once. Replaying recent messages into a fresh Detector is only exact if the replay starts early enough, and the application cannot know how early. Three capabilities answer this together: the **Snapshot**, the **Replay Point** and the **Memory Horizon**.

## Snapshot and Replay Point, both

- **Snapshot: instant, exact, with a format to keep.** `detector.snapshot()` returns the complete state as a byte array; `Detector.builder()...restore(bytes).build()` continues from it without replaying anything. The price is a binary format that later versions must keep readable or reject deliberately.
- **Replay Point: no format, but a replay.** `detector.replayPoint()` tells the earliest timestamp from which a replay into a fresh Detector, with History of the Events Ended before it, reproduces the state. Nothing is stored but a timestamp and the Ended Events, so there is nothing to migrate. It is the fallback when no usable Snapshot exists (none taken yet, Rules changed, Snapshot lost or of an unsupported format version), and it bounds the replay of the messages after the latest Snapshot.
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

## The Replay Point

`replayPoint()` is an `OptionalLong`: empty before the first Sample and with a sampling rate (a fresh Detector cannot continue the Sample index; use a Snapshot there), otherwise a timestamp of a moment (Sample or time advance). Replaying every moment from it on (and `close()` if it was called) into a fresh Detector built with the same Rules and options and the History of every Event that Ended before it, then continuing, never differs from the original from the stop on. Reading it costs a loop over the Channels, moving averages and Rules, nothing proportional to history, and allocates only the result.

What it is made of (the spec's list was a start; the differential tests found the rest):

- **Every piece of state names the earliest moment that reproduces it.** A Channel's value is reproduced from its latest update, and from the one before if `rate()`, `rising()` or `falling()` read it; `age()` needs the latest update even while a Pulse Channel is absent; a moving average's window needs its oldest entry, and a duration window also the entry that proved it warm (the latest one that left by time); the previous Sample behind `maxGap`; the start of the latest Event of a Rule whose Aggregates are read through `latest(...)`; the start of an Event in progress and of a Rule still waiting for `when` to stop holding after an Event that Ended on its End Condition; the moment `when` was last false (it decides `truncatedStart` of the next Event).
- **The point is closed.** Each of those numbers is not the moment itself but the latest moment from which the whole state that moment's evaluation read is available (`Detector.settle`): a decision that lasts, such as an Event starting or `when` being false, is only reproduced by a replay that also has what it read, for example the previous value behind `rate()`. And no Event is split: a replay point never lies inside an Event, or inside the span in which a Rule is still unarmed after one, because an Event that began before it would be neither in History nor produced again. The minimum of closed points is closed, so the Replay Point is the minimum over all the pieces.
- **Expired confirmation times go at every moment** (`count(...).within(...)`). They used to go when a Rule evaluated the count, so `retainedValues()` depended on which Rules had read it and a replay could not match it.

Preconditions, each with a test:

- `droppedValues()` is 0. Once a window or the confirmation times dropped an entry because of the Retention Limit, which entries are held depends on when entries arrived, and a replay that starts later can hold different ones (`ReplayPointTest.aSmallRetentionLimitThatDroppedEntriesIsAPrecondition`). `droppedValues()` itself, a count since the Detector was built, and the one warning logged at the first drop are never reproduced.
- The replay gets the same input in the same order: all Samples and time advances from the Replay Point on, and `close()`.
- A moving average is recomputed from the replayed values. The compensated sum of the original also subtracted the values that left and was recomputed at turnovers, so in principle the last bit of an average can differ when the rounding depends on that order. No difference was found in the generated cases (all comparisons are bit for bit); a Snapshot reproduces the sums exactly.
- Side effects must be suppressed up to the stop by the application: the replay reports Events that already happened.

The Replay Point is also not monotonic and not minimal in general: it is conservative where a Rule may end by a gap in the data in the next moment, and after an Event ends it still points before the Event for the one moment in which it ended.

## Compatibility policy

A Snapshot is readable by any rbed version with the same format version. The format version changes only when the shape of the state changes; a release that changes it says so in its release notes, and rejects older Snapshots with a message pointing to the Replay Point. There is no promise of migrating across format versions: the Replay Point is the way across.

## Alternatives considered

- **Replay only.** Rejected as the only answer: an Event in progress, a window or a `latest(...)` Aggregate reaches back a data-dependent distance, and a restart is slow when the stream is long.
- **Java serialization.** Rejected: it ties the format to class layout and is a known hazard on untrusted input; the explicit format is small and checked.
- **Restoring into different Rules.** Rejected by design: the fingerprint makes a deployment that changed a Rule fall back to a replay instead of continuing from an incompatible state.
