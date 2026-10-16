# 09: Aggregates and Pulse Channels pages

**What to build:** Two concept pages. Aggregates: clicking an Event shows its per-Channel minimum, maximum and mean, and the Event table gains Aggregates columns, on this page and on every later Scenario page. Pulse Channels: Pulse Channel values are drawn as marks at their Samples, not as a line, since they have no value between Samples; the result keeps absent values absent. See spec: User Stories 19 and 22.

**Blocked by:** 04

**Status:** done

- [x] Selecting an Event shows its Aggregates per Channel
- [x] The Event table shows Aggregates
- [x] Pulse Channels are drawn as marks; Samples without a Pulse value show none
- [x] `run` keeps Pulse Channel values absent where rbed has none
- [x] JUnit and the Node parity test cover both Scenarios at their defaults

## Comments

**Done.** Checked in headless Chromium (Playwright from a scratch directory) at 1280 and 375 px, light and dark, on `/aggregates/`, `/pulse-channels/` and the front page: no horizontal scroll, no page errors. Scripted: the Aggregates rows of the table, the panel on selecting an Event (and gone on deselecting), `?threshold=7` and `?window=45` change the Events, the hover readout shows `deploy –` between pulses.

- **Bridge:** every Event in `run` has `aggregates`: by Channel name (in the Scenario's Channel order, only the Channels the Rule aggregates) `{min, max, mean, count, timeWeightedMean, integral}`, NaN as null. The catalogue has `pulse` (names of the Pulse Channels). rbed does not expose a Channel's kind, so a Scenario overrides `List<Channel> pulseChannels()` (default empty) in `Scenario`. Samples already left absent values out, so Pulse values stay absent without extra code; `pulseValuesStayAbsentInSamplesThatDoNotCarryThem` pins it.
- **Scenarios:** `aggregates` (`AggregatesScenario`: a vehicle's speed and engine temperature, logged irregularly, `Drive` with `.aggregate(temp)`; parameter `threshold`) and `pulse-channels` (`PulseChannels`: errors per minute and the Pulse Channel `deploy` at minutes 10 and 60; `Deployment`, `Regression` via `after(...).within(...)`; parameters `window`, `limit`). `Bridge.SCENARIOS` now lists one Scenario per line, so parallel tickets add a line each.
- **Event table:** each Event row is followed by an `aggregateRow` (`colSpan=5`) with one line per Channel, `temp: min 75, max 85, mean 80` (or `temp: no value` when `count` is 0), so the table stays five columns wide on a phone. A column added later (05: Discarded) has to raise that `colSpan`. Selecting an Event (row button or band) highlights both rows and shows `AggregatesPanel` under the chart: every component, a column per Channel.
- **Shared formatting:** `src/aggregates.ts` (imports nothing) has the `Aggregates` type, `formatNumber` (two decimals, null as a dash) and `aggregatesLine`, used by the live table and by `eventRows` in `prerender.mjs`. The prerendered table and the twin have an `Aggregates` column with the lines joined by `; `.
- **Pulse Channels** are drawn as a `Scatter` (radius 5) from the chart rows; a missing value is a gap, so a Sample without a pulse shows no mark.
- **umriss-ui gaps:** none new. The Channels share one course axis, so Channels of very different ranges (speed 0 to 14 m/s next to temperature 60 to 77 °C on the Aggregates page) squash each other; a second course axis per unit would need the kit's lane arithmetic per axis.
