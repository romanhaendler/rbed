# 04: Pulse Channels

**What to build:** Users declare markers with `Detector.builder().pulse("deploy")`. A Pulse Channel has a value only in the Sample that carries it, and is undefined otherwise, so a one-off marker no longer reads as permanently set. See spec (8).

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Pulse Channel is undefined in Samples without it; comparisons on it are false then
- [x] Rate, rising/falling and averages of a Pulse Channel only see Samples carrying it
- [x] Aggregates count only Samples carrying it
