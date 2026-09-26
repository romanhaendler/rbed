# 06: History-based expressions

**What to build:** A user can write Conditions on how a Channel develops: `rising()`, `falling()`, `rate()` (per second), and `movingAverage` over N Samples or a time span. Until enough Samples exist for a value to be defined, Conditions using it are false (warm-up).

**Blocked by:** 02 (Condition vocabulary and sample-and-hold)

**Status:** ready-for-agent

- [ ] `rising`/`falling` compare with the previous Sample that contained the Channel
- [ ] `rate()` is the change per second between the last two values of the Channel
- [ ] `movingAverage(n)` and `movingAverage(duration)` keep only their window
- [ ] Undefined values during warm-up make Conditions false
