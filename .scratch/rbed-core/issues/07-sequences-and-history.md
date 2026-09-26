# 07: Sequences and History

**What to build:** A user can write Rules that depend on other Rules' Events: `during(A)` (an A Event is Confirmed and not Ended), `after(A)` (an A Event has Ended), narrowed by `unless(C, ...)` (no C Confirmed since that A Ended) and `within(window)`. Rules are evaluated in declaration order, and referencing oneself or a later Rule fails at build time. A user can pass History (Events from before the Detector started) that counts for `after`/`unless` but is not reported. See ADR-0002.

**Blocked by:** 02 (Condition vocabulary and sample-and-hold), 03 (Durations and the Pending phase)

**Status:** done

- [x] `during`, `after`, `unless` and `within` behave as specified in the spec
- [x] A Rule sees effects of earlier Rules on the same Sample
- [x] Self or forward references throw `IllegalArgumentException` at `build()`; names no Rule produces are allowed
- [x] History Events enable `after` from the first Sample and are not passed to the listener
