# 06: History-based expressions

**What to build:** A user can write Conditions on how a Channel develops: `rising()`, `falling()`, `rate()` (per second), and `averageOverSamples` / `averageOverDuration`. Until enough Samples exist for a value to be defined, Conditions using it are false (warm-up).

**Blocked by:** 02 (Condition vocabulary and sample-and-hold)

**Status:** done

- [x] `rising`/`falling` compare with the previous Sample that contained the Channel
- [x] `rate()` is the change per second between the last two values of the Channel
- [x] `averageOverSamples(n)` and `averageOverDuration(duration)` keep only their window
- [x] Undefined values during warm-up make Conditions false
