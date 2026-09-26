# Gaps found by the examples

**Status: all resolved** by the extensions in `.scratch/rbed-extensions/` (spec and tickets). The examples now use the features instead of workarounds. The column "Resolved by" names them.

Ten end-to-end examples (`src/test/java/rbed/CraneScenarioTest.java` and `src/test/java/rbed/examples/`) exercise the library across domains. Where an example needed something the library cannot express, it uses a workaround and names the gap. Ordered by estimated value.

| # | Gap | Found in | Workaround before | Resolved by |
|---|-----|----------|------------------|-------------|
| 1 | **Timeouts without new Samples.** `maxGap` is only noticed when the next Sample arrives, so "sensor silent for 10 s" cannot alarm while the sensor stays silent. | VitalSigns (the dropout is only reported once data resumes) | Caller runs its own timer. | `advanceTo(t)` + `channel(...).age()` (VitalSigns: SensorLost) |
| 2 | **Values of earlier Events in Conditions**, e.g. "weight fell by half of the lifted load" or "recovery time after a sprint". | IntervalTraining, crane (partial set-down, discussed earlier) | Listener computes it; cannot drive detection. | `latest("A").mean/min/max/duration` (crane: PartialSetDown) |
| 3 | **Arithmetic on Expressions** (difference, ratio, scaling, abs), e.g. supply/return temperature difference, "5 % above the average", vibration magnitude. | Not needed by the ten examples as written, but typical in HVAC, trading and vibration monitoring | Caller pre-computes a derived Channel. | `plus/minus/times/dividedBy/abs` (crane: PartialSetDown uses `times(0.5)`) |
| 4 | **Counting Events in a window**, e.g. "more than 3 compressor starts per hour", "number of reps". | HvacShortCycling, IntervalTraining | Listener counts. | `count("A").within(...)` (HvacShortCycling: FrequentStarts, IntervalTraining: SessionDone) |
| 5 | **Cumulative duration across Events**, e.g. total time above 8 °C over a trip. | ColdChain | Listener sums durations. | `totalDuration("A")` (ColdChain: CumulativeLimit) |
| 6 | **Smoothed history functions**: `rate()`, `rising()` and `falling()` only work on raw Channels, not on a moving average, so noisy signals flicker. | FloodWarning | Caller pre-smooths into a derived Channel. | history functions on `averageOver...` (FloodWarning) |
| 7 | **Time-weighted mean / integral** in Aggregates. The mean weights every Sample equally, which is biased under irregular sampling, and there is no integral (e.g. distance from speed). | FleetTelematics | Mean x duration, exact only with regular sampling. | `Aggregates.timeWeightedMean()` / `integral()` (FleetTelematics: distance) |
| 8 | **Pulse (marker) Channels.** Sample-and-hold makes a one-off marker read as permanently set. | DeploymentRegression | Push the marker as 0 on the next Sample. | `Detector.builder().pulse(...)` (DeploymentRegression) |

Inherent to streaming, not a gap: `during(A)` stays true until A's End Condition is *confirmed*, although A's end timestamp is where the End Condition *began* (MachineToolOverheat). Retracting earlier state would require delaying all output.
