# 03: Durations and the Pending phase

**What to build:** A user can require a Condition to hold for N consecutive Samples (`forSamples`) or for a time span (`forDuration`, in ms or `Duration`). Events become Pending first and are Confirmed once the requirement is met, or Discarded if the Condition stops holding earlier. An Event already in progress at the Detector's first Sample is flagged truncatedStart. `close()` Discards a Pending Event.

**Blocked by:** 01 (Walking skeleton)

**Status:** ready-for-agent

- [ ] `forSamples(n)` confirms on the n-th consecutive true Sample (counting the first)
- [ ] `forDuration(d)` confirms on the first Sample with timestamp − start ≥ d
- [ ] A Pending Event whose Condition fails is reported via `onDiscarded`
- [ ] truncatedStart is set when Pending begins at the first Sample
- [ ] `close()` Discards a Pending Event
