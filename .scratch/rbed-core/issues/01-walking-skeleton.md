# 01: Walking skeleton

**What to build:** A user can add the library (Maven, Java 17, JPMS module `rbed`, zero runtime dependencies), build a Detector with one Rule using `channel(x).above(c)`, push timestamped Samples, and receive `onPending`, `onConfirmed` and `onEnded` callbacks. Without a duration requirement, Pending and Confirmed are reported on the same Sample, and the Event Ends on the first Sample where the Condition no longer holds. `close()` Ends a running Event with a Truncated end. See spec: `.scratch/rbed-core/spec.md`.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `mvn verify` builds and tests with JUnit 5 as the only (test-scope) dependency
- [x] `EventListener` has default no-op methods for all four lifecycle callbacks
- [x] An Event has name, start, end (absent until Ended), truncatedStart and truncatedEnd flags
- [x] Threshold crossing produces Pending+Confirmed at the crossing Sample and Ended at the first Sample below
- [x] `close()` Ends a Confirmed Event with truncatedEnd and end = last Sample timestamp; pushing after close throws `IllegalStateException`
- [x] Non-increasing timestamps and NaN/infinite values throw `IllegalArgumentException`
- [x] Tests exercise only the public API with a recording listener
