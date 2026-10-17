# Spec: reading an Expression's value, and two README corrections

Status: done

## Problem Statement

Three problems surfaced while building the documentation site (spec `rbed-interactive-docs`):

1. **The README's truncation example is wrong.** In "The Event lifecycle", the example that is meant to show `truncatedStart()` pushes 90 at 0 and 70 at 1000 and claims `Overheat 0..1000 with truncatedStart() == true`. But `Overheat` is defined just above with `forDuration(2_000)`, so that Pending Event is Discarded at 1000 and no Event is reported at all (run against rbed 0.2.0). A reader who copies the example sees nothing and is left confused about truncation.
2. **The README's sentence on when a Rule's next Event may start is imprecise.** It says "A new Event of a Rule only starts after its `when` Condition has stopped holding since the previous Event started." What rbed does is narrower: while an Event with an End Condition is in progress, `when` is not evaluated at all, so a moment in which `when` stopped holding during that Event does not count; the next Event only starts once `when` has been seen not holding at a Sample after the Event Ended. After a data gap longer than `maxGap`, the next Sample may start an Event at once, with a Truncated start. A reader relying on the README predicts Events that never happen (the site's End Conditions page shows four Events where the sentence suggests seven).
3. **rbed cannot tell a user the value of an Expression.** A user can only compare an Expression (`above`, `below`, `between`); the current value of `temp.averageOverSamples(5)`, `power.rate()`, `hr.age()` or `count(run).within(...)` is invisible. Users who want to log, plot or check the values their Rules compare against have to re-implement rbed's arithmetic, which drifts from the library. The documentation site works around it with a bisection: about 64 extra Detector runs per call, reading each value through comparisons against a probe Channel. It is exact but slow, indirect and only covers Sample moments.

## Solution

1. The README's truncation example is replaced by one that really produces a Truncated start with the same `Overheat` Rule: hot at 0, 1000 and 2000, cooler at 3000, giving `Overheat 0..3000`, Confirmed at 2000, with `truncatedStart() == true` (verified against rbed 0.2.0).
2. The README's sentence is replaced by a precise statement of the rule, including the End Condition case and the data-gap exception, consistent with what the site's End Conditions and Data gaps pages already say.
3. rbed gains a small public way to read an Expression's value. An Expression is **watched** by registering it on the Detector's builder; after any Sample or time advance, the Detector reports that Expression's current value, or reports that it is undefined. The README explains it, and the documentation site reads its plotted Expressions this way instead of by bisection, so its plots cost one run and also show values at time advances.

## User Stories

1. As a reader of the README, I want the truncation example to produce exactly what its comment claims, so that I can trust the README's examples.
2. As a reader of the README, I want the truncation example to use the Rule defined just above it, so that I see truncation and a duration in one example.
3. As a reader of the README, I want to see that the Event's start (0) lies before its Confirmation (2000) even when truncated, so that I understand that truncation concerns the start, not the confirmation.
4. As a reader of the README, I want a precise statement of when a Rule's next Event may start, so that I can predict how many Events a flapping Channel produces.
5. As a reader of the README, I want to know that `when` is not evaluated while an Event with an End Condition is in progress, so that I understand why a dip during the Event does not allow an immediate restart.
6. As a reader of the README, I want to know the data-gap exception, so that I am not surprised by an Event with a Truncated start right after a gap.
7. As a maintainer, I want both README statements pinned by library tests, so that the README cannot drift from the behaviour again.
8. As a user of rbed, I want to read the current value of any Expression I use in my Rules, so that I can log or display what my Conditions compare.
9. As a user of rbed, I want to read derived values such as a moving average, a rate, an age or an integral, so that I do not have to re-implement rbed's arithmetic.
10. As a user of rbed, I want to read Expressions about other Rules' Events, such as `count(rule).within(...)`, `totalDuration(rule)` or `latest(rule)...`, so that I can show how close a limit is.
11. As a user of rbed, I want to read an Expression that no Rule uses, so that I can observe values without writing a dummy Rule.
12. As a user of rbed, I want to be told clearly when an Expression's value is undefined (for example during a moving average's warm-up, or before the first Sample), so that I never mistake "unknown" for a number.
13. As a user of rbed, I want a Pulse Channel that is absent in the current moment to read as having no value, so that its value is never held from an earlier Sample.
14. As a user of rbed, I want the value read after a time advance to reflect that moment (for example `age()` grown), so that silent sensors can be observed.
15. As a user of rbed, I want reading to work in sampling-rate mode and in batch processing, so that it fits every way of feeding a Detector.
16. As a user of rbed, I want to read values from within my EventListener callbacks, so that I can record what an Expression was when an Event became Confirmed or Ended.
17. As a user of rbed, I want asking for an Expression that I did not register to fail immediately with a clear message, so that a forgotten registration is not mistaken for an undefined value.
18. As a user of rbed, I want a watched Expression that refers to a Rule not added to the Detector to be rejected by `build()`, as Rules are, so that mistakes surface early.
19. As a user of rbed, I want a watched `averageOverSamples(n)` above the Retention Limit to be rejected by `build()` with a message that names the watched Expression (not a Rule), so that I know what to fix.
20. As a user of rbed, I want watching to cost nothing on Samples where I do not read, so that the Detector's steady-state performance and its zero-allocation push stay as documented.
21. As a user of rbed, I want watching an Expression to change no Event, so that adding observation never changes which Events are found.
22. As a user of rbed, I want the README to show how to watch and read an Expression, with a short example, so that I find the feature.
23. As a user of rbed, I want the Javadoc of the new methods to state when a value is undefined, so that the contract is clear in my IDE.
24. As a reader of the documentation site, I want the plotted Expression lines to be read from rbed directly, so that what I see is exactly rbed's value.
25. As a reader of the documentation site, I want the silent sensors page to show `hr.age()` growing at time advances, so that I see the silence measured.
26. As a reader of the documentation site, I want the Conditions and Expressions page to show how to read an Expression's value in Java, so that I can copy it.
27. As the maintainer, I want the site's bisection workaround removed, so that there is less code and no per-call cost of about 64 runs.
28. As the maintainer, I want the site to keep showing exactly the same Expression values as before at every Sample, so that the change is provably safe.
29. As the maintainer, I want the decision recorded in an ADR, so that the reasons for a pull-style, registered read are kept.
30. As the maintainer, I want the change to need a new minor version, so that users can see a new API appeared.

## Implementation Decisions

- **README, truncation example.** Replace the two-push example under "Truncation" in "The Event lifecycle" with four pushes on a fresh Detector with `Overheat`: 90 at 0, 92 at 1000, 91 at 2000, 70 at 3000; the comment states `Overheat 0..3000, Confirmed at 2000, truncatedStart() == true`. Verified against rbed 0.2.0 (the original example gives only a Discarded Pending Event).
- **README, restart rule.** Replace the sentence "A new Event of a Rule only starts after its `when` Condition has stopped holding since the previous Event started." with a precise statement, along these lines: "After an Event has Ended, the Rule's next Event only starts once its `when` Condition has been seen not holding at a later Sample; while an Event with an End Condition is in progress, `when` is not evaluated. After a data gap longer than `maxGap`, the next Sample can start an Event at once, marked `truncatedStart()`." The following sentence about End Conditions that overlap `when` (no flicker) stays. The wording must match the site's End Conditions and Data gaps pages; if they differ, align both.
- **No library behaviour changes for items 1 and 2.** The behaviour is intended (it prevents flapping) and only the documentation was wrong.
- **New public API (item 3):**
  - `Detector.Builder.watch(Expression... expressions)`: registers Expressions whose values the built Detector will report. It may be called more than once; watching the same Expression twice is harmless.
  - `Detector.value(Expression expression)`: returns an `OptionalDouble` with the Expression's value at the latest moment processed (the latest Sample or time advance), or empty while it is undefined (no Sample yet, warm-up, a Pulse Channel absent in that moment, or any other undefined case). It may be called at any time, including from EventListener callbacks during a push and after `close()` (then it reports the last moment).
  - Asking for an Expression that was not watched throws `IllegalArgumentException` naming the Expression. Expressions are matched as they are matched elsewhere: a Channel by name, every other Expression by identity.
  - The names `watch` and `value` are the decision; the exact Javadoc wording is the implementer's.
- **Binding.** Watched Expressions are bound in `build()`, after all Rules, so they may refer to any added Rule. A reference to a Rule not added is rejected as for Rules. Messages produced while binding (for example `averageOverSamples(n)` above the Retention Limit) name the watched Expression instead of a Rule. A watched Expression's Channels count as used Channels (their values are kept), which `retainedValues()` reflects.
- **No extra evaluation step.** Channel-derived state (moving averages, rate, age, integrals) already updates when a Channel updates, and Event-based Expressions read the Detector's Event state; so reading a watched Expression is a lookup of its bound value at the moment it is asked for. Pushing a Sample allocates nothing more and performs no extra work when nobody reads.
- **ADR-0006** records: values are read pull-style from the Detector, not pushed to a callback; Expressions must be registered because binding happens once in `build()`; an unwatched read fails fast (in the spirit of ADR-0003); undefined is an empty `OptionalDouble`, matching `Event.end()`'s `OptionalLong`.
- **README.** A short subsection "Reading an Expression's value" (in "Conditions and Expressions") with a small example that watches a moving average and prints it from a listener, and a note that undefined reads as empty. The README's table of contents is updated if needed.
- **Version.** The pom version is raised to 0.3.0 (a new public API). Releasing follows RELEASING.md and is a separate act of the maintainer.
- **Documentation site.** The Scenario module watches each plotted Expression and reads it after every push and advance in the shared feeding helpers (`Lifecycle.push`/`advance`), instead of `ExpressionValues`' bisection, which is removed with its tests that only concern the bisection. The result's plotted values gain entries at advance moments (silent sensors). The site keeps building against the rbed of the same commit, so it uses the new API before any release. The site README, ADR-0005's note on reading Expression values and the Conditions and Expressions page (a short "Read an Expression's value" passage with a Java snippet that SourceTest compiles) are updated. The Javadoc links on the site resolve against the generated Javadoc, which now contains the new methods.

## Testing Decisions

- **Good tests check what a user can observe through the public API:** values read after given Samples and advances, empty reads where the value is undefined, build-time rejections and their messages, unchanged Events. They do not inspect bound internals.
- **Seams (two, both existing):**
  - The rbed library's public API (`Detector`, `Detector.Builder`), tested with JUnit like the existing feature tests.
  - The documentation site's bridge (`scenarios()` / `run(...)`), tested by the existing BridgeTest, parity and SourceTest; no new seam.
- **Library tests:**
  - The corrected README truncation example and the restart rule (End Condition case and data-gap case) are pinned, in the style of DurationTest and EndConditionTest.
  - `watch`/`value`: a Channel; a moving average over Samples during and after warm-up (empty, then values); `rate()`; `age()` across `advanceTo`; `count(rule).within(...)` and `totalDuration(rule)`; a Pulse Channel present and absent; an Expression no Rule uses; reading inside `onConfirmed`; sampling-rate mode and `detect(...)`; after `close()`.
  - Rejections: unwatched read; watched Expression referring to a Rule not added; watched `averageOverSamples(n)` above the Retention Limit, with the message naming the Expression.
  - Watching does not change the Events of an existing Scenario (compare with and without watching), and the existing steady-state allocation checks still pass with watched Expressions.
- **Site tests:** BridgeTest's existing assertions on plotted values must pass unchanged at every Sample (they pin rbed's exact values); new assertions only for values at advance moments; parity and SourceTest pass; the browser smoke tests pass.
- **Prior art:** DurationTest, EndConditionTest, MovingAverageFunctionsTest, TimeAdvanceTest, PulseChannelTest, RetentionTest (rejections and messages), the allocation checks in the performance tests; on the site, BridgeTest's plot assertions and `assertPlotAboveLimitExactlyDuring`.

## Out of Scope

- Changing rbed's restart behaviour (only its documentation is corrected).
- A push-style API (a callback per moment) or reading a whole history of values from the Detector.
- Reading Conditions' truth values or Rule states through the new API.
- Releasing 0.3.0 to Maven Central (the maintainer's step, per RELEASING.md).
- The `@umriss-ui/charts` gaps found while building the site.
- Changes to the site's pages beyond what reading Expression values touches.

## Further Notes

- The README truncation fix was verified against rbed 0.2.0: the current example yields only `discarded`; the replacement yields `Overheat 0..3000`, Confirmed at 2000, `truncatedStart() == true`. A variant with `HighTemperature` (no duration) also works (`0..1000`, truncated start) but would leave the `Overheat` thread of the chapter.
- The restart behaviour was measured on the site's End Conditions Scenario (four Events at End threshold 500 where the README's sentence suggests seven) and is pinned there in BridgeTest; the library test added here pins it at the source.
- The site's bisection (`ExpressionValues`) was exact (checked bit for bit, including negatives, ±0, subnormals and extremes), so its plotted values at Samples must equal the new reads exactly; any difference is a bug in the new API.
