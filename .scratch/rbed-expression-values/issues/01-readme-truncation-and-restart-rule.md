# 01: README: truncation example and restart rule, pinned by tests

**What to build:** The README's "The Event lifecycle" chapter tells the truth. The truncation example uses `Overheat` on a fresh Detector with 90 at 0, 92 at 1000, 91 at 2000 and 70 at 3000, and its comment states `Overheat 0..3000, Confirmed at 2000, truncatedStart() == true`. The sentence on when a Rule's next Event may start is replaced by the precise rule: after an Event has Ended, the next Event only starts once `when` has been seen not holding at a later Sample; while an Event with an End Condition is in progress, `when` is not evaluated; after a data gap longer than `maxGap`, the next Sample can start an Event at once, marked `truncatedStart()`. Library tests pin both statements so the README cannot drift again. No library behaviour changes. See spec: Problem Statement 1 and 2; Implementation Decisions "README, truncation example", "README, restart rule".

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] The README's truncation example is replaced as described, and its comment matches what rbed reports
- [ ] The README's restart sentence is replaced by the precise rule; the following sentence about End Conditions overlapping `when` stays
- [ ] A library test runs the README's truncation example exactly as printed and asserts the commented outcome
- [ ] A library test pins the restart rule with an End Condition: a dip of `when` during a running Event does not allow a new Event right after it Ends; the next Event starts only after `when` was seen not holding at a later Sample
- [ ] A library test pins the data-gap exception: after a gap longer than `maxGap`, the next Sample starts an Event with a Truncated start
- [ ] The site's End Conditions, Data gaps and Event lifecycle pages say the same as the README (adjust wording on either side if they differ)
- [ ] `mvn -q verify` at the root and the site's checks pass
