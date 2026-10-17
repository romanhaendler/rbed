# 01: README: truncation example and restart rule, pinned by tests

**What to build:** The README's "The Event lifecycle" chapter tells the truth. The truncation example uses `Overheat` on a fresh Detector with 90 at 0, 92 at 1000, 91 at 2000 and 70 at 3000, and its comment states `Overheat 0..3000, Confirmed at 2000, truncatedStart() == true`. The sentence on when a Rule's next Event may start is replaced by the precise rule: after an Event has Ended, the next Event only starts once `when` has been seen not holding at a later Sample; while an Event with an End Condition is in progress, `when` is not evaluated; after a data gap longer than `maxGap`, the next Sample can start an Event at once, marked `truncatedStart()`. Library tests pin both statements so the README cannot drift again. No library behaviour changes. See spec: Problem Statement 1 and 2; Implementation Decisions "README, truncation example", "README, restart rule".

**Blocked by:** None (can start immediately)

**Status:** done

- [x] The README's truncation example is replaced as described, and its comment matches what rbed reports
- [x] The README's restart sentence is replaced by the precise rule; the following sentence about End Conditions overlapping `when` stays
- [x] A library test runs the README's truncation example exactly as printed and asserts the commented outcome
- [x] A library test pins the restart rule with an End Condition: a dip of `when` during a running Event does not allow a new Event right after it Ends; the next Event starts only after `when` was seen not holding at a later Sample
- [x] A library test pins the data-gap exception: after a gap longer than `maxGap`, the next Sample starts an Event with a Truncated start
- [x] The site's End Conditions, Data gaps and Event lifecycle pages say the same as the README (adjust wording on either side if they differ)
- [x] `mvn -q verify` at the root and the site's checks pass

## Comments

- The truncation example is pinned by `DurationTest.readmeTruncationExample` (pushes copied verbatim from the README; asserts `Overheat 0..3000 truncatedStart` and Confirmed at 2000). The README now says "On a fresh Detector with `Overheat` from above", since the earlier example's `detector` already holds pushes up to 6000.
- The restart rule is pinned in `EndConditionTest`: `whenStoppingDuringTheEventDoesNotReArmTheRule` (dip during the Event does not count), `whenNotHoldingAtTheEndingSampleDoesNotReArmTheRule` (with an End Condition, not even the Ending Sample counts: it must be a later Sample), `withoutEndConditionTheEndingSampleReArmsTheRule`, `discardingSampleReArmsTheRuleEvenWithAnEndCondition` and `dataGapLetsTheNextSampleStartAnEventAtOnce`.
- Deviation from the spec's suggested wording: "at a later Sample" only holds for a Confirmed Event running on its End Condition. Without an End Condition (and for a Discarded Pending Event, which still follows `when`), the Sample at which the Event Ends or is Discarded already counts, so the next Sample can start a new Event. The README says this explicitly, and "while an Event with an End Condition is in progress" became "while a Confirmed Event runs on its End Condition", since `when` is evaluated while Pending.
- The site's Event lifecycle page got the same wording; the End Conditions page now says "at a Sample after the previous one Ended (or right after a data gap longer than `maxGap`)". The Data gaps page already matched.
