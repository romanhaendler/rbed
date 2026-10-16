# 06: End Conditions and data gaps pages

**What to build:** Two concept pages. End Conditions: a noisy Channel near a threshold, with controls to move the End Condition's threshold apart from the start threshold (hysteresis removes flapping Events) and to give the End Condition its own duration. Data gaps: a Scenario with a gap in its Samples, with controls to move the gap and to change `maxGap`, showing when an Event is ended at the gap or kept across it. Truncated start and end are marked on the bands. See spec: User Stories 9 to 12.

**Blocked by:** 05

**Status:** done

- [x] Moving the End Condition's threshold apart from the start threshold reduces the number of Events on the noisy Channel
- [x] The End Condition's duration control delays the end of Events visibly
- [x] Moving the gap or changing `maxGap` switches an Event between ended at the gap and kept across it
- [x] Truncated starts and ends are marked on the bands and named in the text alternative
- [x] JUnit pins one hysteresis change and one `maxGap` change per page, and the Node parity test covers them

## Comments

**Done.** Checked in headless Chromium (Playwright from a scratch directory) at 1280 and 375 px, light and dark, on `/end-conditions/`, `/data-gaps/` and the front page: no horizontal scroll, no page errors. Scripted: the Event table and text alternative at the defaults and at every state the page text names (`?end=450`, `?end=450&endDuration=1`, `?end=0`, `?maxGap=6`, `?gap=2`), and the maxGap slider by keyboard (address follows).

- **Scenarios:** `end-conditions` (`EndConditions`: a crane's `weight` around 500 kg with a swing to 440 at 14 s, set down at 22 s; `LoadLifted` `.when(weight.above(500)).endWhen(weight.below(end)).forDuration(endDuration)`; parameters `end` 0..500 kg, default 500, and `endDuration` 0..5 s) and `data-gaps` (`DataGaps`: `rpm` 1500 from 6 to 31 s, 40 Samples a second apart, a 6 s gap; `Running` with `maxGap`; parameters `gap` 0..33 s, default 12, and `maxGap` 1..10 s, default 5).
- **Bridge:** the Samples may now depend on the parameters: `Scenario.samples(Map<String, Double> values)` (default: `samples()`), which `run` calls with the parsed values. `DataGaps` overrides it to leave the Samples inside the gap out. The feeding loop is unchanged, so `lifecycle.now` stays current.
- **ScenarioView (every page):** a Truncated start or end is a grey halo (Scatter radius 9, `TRUNCATED`) behind the lifecycle mark at that edge, drawn before the marks; the key has "Truncated start or end" (`.swatch.halo`). The text alternative and the table already named Truncated flags.
- **rbed behaviour found (pinned in BridgeTest, written into the page):** with an End Condition, `when` is not evaluated while the Event is Confirmed, so the Sample at which the End Condition Ends the Event does not count as `when` not holding: a new Event starts only after `when` has been seen not holding at a later Sample (at end 500 the weight at 6 s and 7 s is above 500, yet the next Event starts at 10 s). The README's "only starts after its `when` Condition has stopped holding since the previous Event started" is loose on this point (not changed). An End Condition's duration does not move `end()`; `onEnded` comes when the duration is reached (verified with a throwaway probe: at 23 s for 1 s, 24 s for 2 s). The chart's Ended mark is at `end()`; the result has no "Ended at" moment.
- The README's wrong truncation example (see 05) is not repeated; the data gaps page demonstrates Truncated start and end with Scenario data instead.
- **umriss-ui gaps:** none new. A `Line` cannot be broken at a gap without a null row, so the gap shows as a straight segment without markers.
- **Merged with 07 and 12:** `Scenario.detect` now takes the Samples: `detect(Rule[] rules, List<Sample> samples, Lifecycle lifecycle)`, and `run` passes `scenario.samples(values)`, so the result and the Detector see the same Samples. `Manufacturing` and `Healthcare` overrides take the new parameter (Healthcare ignores it, as it ignored `samples()`). `pages.ts` and `Bridge.SCENARIOS` list End Conditions and Data gaps before Conditions and Expressions (README order).
