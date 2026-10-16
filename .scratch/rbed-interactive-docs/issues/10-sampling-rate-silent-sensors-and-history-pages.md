# 10: Sampling rate, silent sensors and History pages

**What to build:** Three concept pages for behaviour beyond plain pushing. Batch and sampling rate: a toggle between push-based and sampling-rate mode on the same Samples, showing how durations are measured differently. Silent sensors: a Scenario whose sensor stops reporting while time is advanced with `advanceTo`, showing `age()` and Events ending without Samples. History: a restart in the middle of the data, with a toggle to supply History, showing a Rule that depends on earlier Events behaving differently with and without it. The Scenario contract gains what these need (a sampling mode, timestamps to advance to, a restart point with History). See spec: User Stories 20, 21, 23.

**Blocked by:** 05

**Status:** done

- [x] Toggling sampling-rate mode changes the Events as rbed's README describes
- [x] The silent-sensor page shows time advancing without Samples and an Event ending there
- [x] Toggling History on the restart changes the dependent Rule's Events
- [x] JUnit pins each toggle's effect, and the Node parity test covers both states of each page

## Comments

**Done.** Three Guide pages: `/sampling-rate/` ("Batch processing and sampling rates"), `/silent-sensors/` ("Silent sensors: advancing time") and `/history/` ("Restarts: History"), in README order (after Aggregates, around Pulse Channels). Checked in headless Chromium (Playwright from a scratch directory) at 1280 and 375 px, light and dark, on the three pages, the front page and `/healthcare/`: no horizontal scroll, no page or console errors. Scripted: each page's choice flips the Event table, the Discarded table, the text alternative and the address (`?mode=1`, `?timer=1`, `?history=1`); `?history=1&restart=120` reproduces that state. Every README claim the pages restate was run against rbed (jshell); all hold.

- **Toggles are ticket 07's choices**, not a kind of their own: a two-option `Parameter.choice` whose option sources are the two code variants (`detect(recording)` vs `samplingRate(${rate})` + `detectValues`; a timer's `advanceTo` vs nothing; `.history(stored)` vs nothing). So `usedParameters` hides "Declared rate" while pushing with timestamps. A Scenario reads a choice as its option index (`values.get("mode") == 0`).
- **Scenario contract** (additive): `detect(Rule[] rules, List<Sample> samples, Map<String, Double> values, Lifecycle lifecycle)`, which `Bridge.run` now calls and which defaults to ticket 06's `detect(rules, samples, lifecycle)`; override it when the feeding depends on the parameters. `samples(values)` (06) gives the chart the timestamps rbed derives in sampling-rate mode.
- **Lifecycle** gained `advance(detector, timestamp)` (sets `now`, calls `advanceTo`, records the advance) and `restart` (Long, the first timestamp after a restart). Use `advance` in every Scenario that advances time; Healthcare now does, so its chart shows the dropout's advances too.
- **Result of `run`**: `"advances":[...]` (always, possibly empty) after `discarded`, and `"restart": t` only when the Detector was restarted. `bridge.ts` types both.
- **ScenarioView (every page)**: advances are small muted marks just above the lanes (`Scatter` named "Time advance", and a key entry when there are any); a restart is a vertical `LimitLine` labelled "Restart". The text alternative appends "Time is advanced without a Sample N times, from a to b." and "The Detector is restarted at t." (through 07's `also`). The hover readout snaps to Samples, so it cannot be read at an advance.
- **Scenarios**: `sampling-rate` (`SamplingRate`: 10 Hz accelerometer with the Samples from 2.1 to 2.9 s lost; `Vibration` above 3 for 1.5 s; with timestamps 1.5..3.5 and 5..7 s, at a declared 10 Hz the first is Discarded at 2.6 s and the second is 4.1..6.1 s), `silent-sensors` (`SilentSensors`: `hr` silent 25..44 s; `HighHeartRate` = above 120 and not older than the limit, `SensorLost` = older; with the timer they switch at 30 s, without it `HighHeartRate` runs to 45 s and `SensorLost` never happens), `history` (`History`: daily service mode, maintenance on days 10 and 140, `OverdueService` = `after(maintenance).within(90 days).not()`, restart day 20..180; with History overdue 102..141, without it 60..141 truncatedStart; at restart 120 both agree). The restart closes the first Detector (gracefully), and the new one gets `lifecycle.ended` as History.
- Restart and sampling rate are not combined in one Scenario; a Scenario that wants both must compute the index per Detector itself.
- **umriss-ui gaps:** a `LimitLine orientation="x"` puts its label on the x axis, where at 375 px it covers a tick label ("Restart" over "100" on the History page); a label inside the plot area (top) would avoid it. The Tooltip snaps to rows, so moments without a row (advances) cannot be hovered.
