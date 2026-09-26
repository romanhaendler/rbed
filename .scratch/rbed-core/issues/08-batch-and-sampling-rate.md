# 08: Batch detection and sampling rate

**What to build:** A user can run a recorded series through a Detector in one call: `detect(...)` over timestamped `Sample`s returns all Ended Events in the order they Ended (ties in Rule declaration order). With `samplingRate(hz)`, the user can push or detect plain value maps without timestamps (timestamp = round(index × 1000 / hz)). A Detector is single-use.

**Blocked by:** 01 (Walking skeleton)

**Status:** ready-for-agent

- [ ] `Sample` record and `detect(...)` for timestamped input
- [ ] `samplingRate` enables timestamp-free `push(values)` and `detect(...)`
- [ ] Mixing timestamped and untimestamped pushes throws
- [ ] `detect` on a used Detector throws `IllegalStateException`; the listener is still called during `detect`
