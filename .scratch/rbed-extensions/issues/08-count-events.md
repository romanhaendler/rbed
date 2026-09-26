# 08: Counting Events in a window

**What to build:** `count("Run").within(Duration.ofHours(1))` is an Expression with the number of Run Events Confirmed in the last hour, History included, so "more than 3 compressor starts per hour" is a Rule. See spec (4).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Count slides with time (also on advances) and includes History
- [ ] Memory bounded by the Events inside the window
- [ ] Reference validation as for `after`
