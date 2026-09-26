# 05: Aggregates and attributes

**What to build:** Every Event carries per-Channel Aggregates (min, max, mean, count) over its Samples from start up to, but excluding, the Sample at which its end was set. A user can attach named attributes computed from these Aggregates when the Event Ends (e.g. classify which load was lifted). Memory stays bounded however long an Event lasts.

**Blocked by:** 01 (Walking skeleton)

**Status:** ready-for-agent

- [ ] `event.aggregates(channel)` exposes min, max, mean and count
- [ ] Aggregates cover the specified Sample range, using held values for Channels absent in a Sample
- [ ] `attribute(name, fn)` values appear in `event.attributes()` on Ended Events
- [ ] Aggregates are available on Confirmed snapshots (so far) and final on Ended
