# 06: Time-weighted mean and integral in Aggregates

**What to build:** Aggregates gain `timeWeightedMean()` and `integral()` (value x seconds) per Channel, using sample-and-hold up to the Event's end (or the last Sample or advance for snapshots). Irregular sampling no longer biases the mean, and distance can be computed from speed. See spec (7).

**Blocked by:** 01 (Aggregates only for the Channels a Rule uses)

**Status:** done

- [x] Worked examples with irregular sampling for both values
- [x] Correct with End Conditions (end phase excluded, and merged back when it resets), Truncated ends and advances
- [x] Zero-span Events give NaN time-weighted mean
