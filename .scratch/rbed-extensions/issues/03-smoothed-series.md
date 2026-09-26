# 03: History functions on smoothed series

**What to build:** `averageOverSamples` and `averageOverDuration` return a `ChannelExpression`, so users can write `channel("level").averageOverSamples(5).rate()` or `...rising()`, and average an average. A derived series updates whenever its source Channel gets a value. See spec (6).

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `rate`, `rising`, `falling` and further averages on averages
- [x] Derived series undefined while warming up; previous value = value at the source's previous update
- [x] Windows of derived series fill from the first Sample, even if only used in an End Condition
- [x] No allocation per Sample
