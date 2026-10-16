# 07: Conditions and Expressions page

**What to build:** A concept page where the reader picks an Expression (rate, moving average over Samples, moving average over a duration, arithmetic between Channels) and sees its value at every Sample plotted as an extra line next to the Channel it is computed from, with the Condition's threshold. Where the Expression is undefined (for example during a moving average's warm-up), the line is absent and the region is marked, and no Event starts there. The bridge's result gains, for Scenarios that ask for it, the Expression's values per Sample. See spec: User Stories 13 and 14; Implementation Decisions "The result of run".

**Blocked by:** 04

**Status:** done

- [x] Choosing an Expression shows its values as a line, with controls for its parameter (window size, Sample count)
- [x] Undefined stretches are marked and carry no Event
- [x] `run` returns the Expression's values per Sample, undefined where rbed's Expression is undefined
- [x] JUnit pins the undefined warm-up length for one moving average, and the Node parity test covers each Expression

## Comments

**Done.** Page `/conditions-and-expressions/` (`src/content/conditions-and-expressions.md`, manifest entry after the front page; tickets 05/06 insert their pages before it, in README order). Checked in headless Chromium at 1280 and 375 px, light and dark: no horizontal scroll, no page errors; switching the Expression (click and arrow keys), the address (`?expression=0`), Reset, the source, the hover readout (`temp.averageOverSamples(5) undefined` in the warm-up) and `?expression=7` (error alert) were scripted.

**How the values are read (decision for the maintainer to confirm):** rbed's public API cannot read an Expression's value, only compare it (`Expression.Bound` and `bind` are package-private). `ExpressionValues` therefore finds the value through the public API alone: a Rule `expression.between(-MAX, MAX)` observed by an EventListener tells where the Expression is defined, and a Rule `expression.above(probe)` against an extra probe Channel, whose value differs per Sample, drives a bisection over the ordered doubles (about 64 Detector runs) down to the exact double rbed computes. No arithmetic of the Expression is repeated in the Scenario module, and the library is untouched. Cost: about 4 to 8 ms per `run` for the 60 Samples here in Node (TeaVM), so within the "frame or two"; a ponytail comment names the ceiling and a K-ary search as the upgrade. If this is too clever, the alternative is a read accessor in rbed, which the spec rules out.

- **Scenario contract:** `Scenario.plot(values)` (default null) returns `Plot(name, expression, threshold)`; `Bridge.run` adds `"expression": {"name", "threshold", "values": [number|null per Sample]}` to the result only then. `Parameter` gained `options` (`Parameter.choice(id, label, defaultIndex, options)`; the 7-argument constructor still makes a number). A choice's value is the option's index, so the address and `src/parameters.ts` stay numeric (`?expression=2`); the bridge rejects a non-index with "Parameter expression has no option 7.0". The catalogue writes `options: [{label, source}]` only for choices.
- **Source:** `${id}` of a choice stands for the option's `source`, which may hold placeholders itself. `fillSource` and `usedParameters` in `src/parameters.ts` (tested in `test/parameters.test.mjs`) do this for the app (`bridge.ts` `source()`) and `prerender.mjs` alike. `ScenarioView` shows a choice as a `RadioGroup` above the sliders and hides sliders that only unchosen options use.
- **Chart (`ScenarioView`):** the Expression is row key `expression`, a dashed `Line` in the next palette colour after the Channels, included in the course axis with its threshold (a `LimitLine`); undefined stretches are grey `LimitBand`s (x) labelled "undefined" (from an undefined Sample to the next one); the readout has an Expression row; the text alternative adds a sentence with the threshold and the undefined stretches (`EventSummary` `also`). The Expression has no "Show" toggle.
- **Data** (`Expressions`, id `expressions`): `temp` every second for 60 s, warm (26) for 6 s, outlier 28 at 15 s, ramp from 30 s to 28; `ambient` 22 from 5 s on. Fixed thresholds per Expression (rate 0.4 °C/s, averages 24, spread 5), because rate and temperature have no common scale for one threshold slider. The page's "Things to try" are checked against the results.
- Writing prose: inline code without spaces does not wrap and caused horizontal scroll at 375 px; long expressions go into fenced blocks.
- **umriss-ui gaps:** a `LimitBand` label stands below the x axis like a legend entry, not inside the band; the `RadioGroup`'s input is covered by its dot, so Playwright must click the label (fine for users).
