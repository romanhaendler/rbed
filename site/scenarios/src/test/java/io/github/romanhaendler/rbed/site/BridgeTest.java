package io.github.romanhaendler.rbed.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * Tests the bridge on the JVM. Every call is also written with its JVM result to
 * target/parity.json, where site/test/parity.test.mjs repeats it against the compiled
 * JavaScript and compares.
 */
class BridgeTest {
  static final List<String> calls = new ArrayList<>();

  @AfterAll
  static void writeParityCases() throws IOException {
    Files.writeString(Path.of("target", "parity.json"), "[" + String.join(",\n", calls) + "]");
  }

  static String call(String function, String result, String... arguments) {
    StringBuilder call = Json.string(new StringBuilder("{\"function\":"), function)
        .append(",\"arguments\":[");
    for (int i = 0; i < arguments.length; i++) {
      if (i > 0) call.append(',');
      Json.string(call, arguments[i]);
    }
    calls.add(call.append("],\"result\":").append(result).append('}').toString());
    return result;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> run(String scenarioId, String parameters) {
    return run(scenarioId, parameters, "{}");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> run(String scenarioId, String parameters, String drawnChannels) {
    String result = Bridge.run(scenarioId, parameters, drawnChannels);
    return (Map<String, Object>) Json.parse(
        call("run", result, scenarioId, parameters, drawnChannels));
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> scenarios() {
    return (List<Map<String, Object>>) Json.parse(call("scenarios", Bridge.scenarios()));
  }

  /** The Events of a result as "Name start..end" plus Truncated flags. */
  @SuppressWarnings("unchecked")
  static List<String> events(Map<String, Object> result) {
    return ((List<Map<String, Object>>) result.get("events")).stream()
        .map(e -> e.get("name") + " " + time(e.get("start")) + ".." + time(e.get("end"))
            + (e.get("truncatedStart") == Boolean.TRUE ? " truncatedStart" : "")
            + (e.get("truncatedEnd") == Boolean.TRUE ? " truncatedEnd" : ""))
        .toList();
  }

  /** The Discarded Pending Events of a result as "Name start..discarded". */
  @SuppressWarnings("unchecked")
  static List<String> discarded(Map<String, Object> result) {
    return ((List<Map<String, Object>>) result.get("discarded")).stream()
        .map(e -> e.get("name") + " " + time(e.get("start")) + ".." + time(e.get("discarded")))
        .toList();
  }

  /** The Confirmed timestamp of each Event of a result. */
  @SuppressWarnings("unchecked")
  static List<String> confirmed(Map<String, Object> result) {
    return ((List<Map<String, Object>>) result.get("events")).stream()
        .map(e -> time(e.get("confirmed")))
        .toList();
  }

  static String time(Object millis) {
    return millis == null ? "?" : Long.toString(((Double) millis).longValue());
  }

  /**
   * The Events of a result as the domain examples' Samples.describe writes them: "Name
   * start..end" with times in the given unit, plus Truncated flags.
   */
  @SuppressWarnings("unchecked")
  static List<String> describe(Map<String, Object> result, long unit) {
    return ((List<Map<String, Object>>) result.get("events")).stream()
        .map(e -> e.get("name") + " " + time(e.get("start"), unit) + ".."
            + time(e.get("end"), unit)
            + (e.get("truncatedStart") == Boolean.TRUE ? " truncatedStart" : "")
            + (e.get("truncatedEnd") == Boolean.TRUE ? " truncatedEnd" : ""))
        .toList();
  }

  static String time(Object millis, long unit) {
    if (millis == null) return "?";
    double t = (Double) millis / unit;
    return t == Math.rint(t) ? Long.toString((long) t) : Double.toString(t);
  }

  /** The note the Scenario gives each Event (its listener-side classification), or null. */
  @SuppressWarnings("unchecked")
  static List<Object> notes(Map<String, Object> result) {
    return ((List<Map<String, Object>>) result.get("events")).stream()
        .map(e -> e.get("note")).toList();
  }

  /** CraneScenarioTest's form: "Name start..end" in ms, then the load of a LoadLifted. */
  @Test
  void cranesAtDefaultsYieldTheCraneScenarioTestsEvents() {
    Map<String, Object> result = run("cranes", "{}");
    List<String> events = describe(result, 1);
    List<Object> notes = notes(result);
    List<String> described = new ArrayList<>();
    for (int i = 0; i < events.size(); i++) {
      described.add(events.get(i) + (notes.get(i) == null ? "" : " " + notes.get(i)));
    }
    assertEquals(List.of(
            "LoadLifted 1500..6500 pallet",
            "Overload 10500..11500",
            "PartialSetDown 11500..12500",
            "LoadLifted 8500..12500 container"),
        described);
  }

  @Test
  void manufacturingAtDefaultsYieldsTheMachineToolOverheatExamplesEvents() {
    assertEquals(List.of(
            "FastHeating 101..141",
            "Overheat 121..331",
            "OverheatUnderLoad 151..391",
            "HighLoad 100..400"),
        describe(run("manufacturing", "{}"), 1000));
  }

  @Test
  void buildingServicesAtDefaultsYieldTheHvacShortCyclingExamplesEvents() {
    Map<String, Object> result = run("building-services", "{}");
    assertEquals(List.of(
            "Run 100..700",
            "QuickRestart 800..920",
            "Run 800..920",
            "Run 1600..2200",
            "FrequentStarts 1630..2390 truncatedEnd"),
        describe(result, 1000));
    // the example's shortCycle: false, true, false for the three Runs
    assertEquals(Arrays.asList(null, null, "short cycle", null, null), notes(result));
  }

  @Test
  void healthcareAtDefaultsYieldsTheVitalSignsExamplesEvents() {
    assertEquals(List.of(
            "Tachycardia 60..159 truncatedEnd",
            "HypoxicTachycardia 130..160",
            "SensorLost 155..170",
            "Desaturation 120..200",
            "Tachycardia 170..300 truncatedStart"),
        describe(run("healthcare", "{}"), 1000));
  }

  @Test
  void automotiveAtDefaultsYieldsTheFleetTelematicsExamplesEvents() {
    Map<String, Object> result = run("automotive", "{}");
    assertEquals(List.of(
            "Idling 60..301",
            "HarshBraking 901..905",
            "Trip 301..904",
            "Idling 904..1499 truncatedEnd"),
        describe(result, 1000));
    // the example's km(trip): 11.94
    assertEquals(Arrays.asList(null, null, "11.94 km", null), notes(result));
  }

  @Test
  void financeAtDefaultsYieldsTheTradingSignalsExamplesEvents() {
    // the example finds the golden cross by brute force over the two averages: day 366
    assertEquals(List.of("DeathCross 199..366 truncatedStart", "GoldenCross 366..599 truncatedEnd"),
        describe(run("finance", "{}"), 86_400_000));
  }

  @Test
  void aLongerSlowAverageStartsTheDeathCrossLater() {
    assertEquals("DeathCross 249..",
        describe(run("finance", "{\"slow\": 250}"), 86_400_000).get(0).substring(0, 16));
  }

  @Test
  void environmentGapShorterThanTheMissingReportInterruptsTheWarning() {
    assertEquals(List.of("FloodWarning 5.25..19.75 truncatedEnd", "Receding 12.25..26"),
        describe(run("environment", "{\"gapMinutes\": 15}"), 3_600_000));
  }

  @Test
  void logisticsAtDefaultsYieldsTheColdChainExamplesEvents() {
    assertEquals(List.of(
            "DoorOpen 60..65",
            "Excursion 62..90",
            "ExcursionAfterDoor 65..90",
            "Excursion 300..330",
            "CumulativeLimit 318..479 truncatedEnd"),
        describe(run("logistics", "{}"), 60_000));
  }

  @Test
  void environmentAtDefaultsYieldsTheFloodWarningExamplesEvents() {
    assertEquals(List.of("Receding 12.25..26", "FloodWarning 5.25..31.25"),
        describe(run("environment", "{}"), 3_600_000));
  }

  /** IntervalTrainingExample's Events, and its listener's recovery times as notes. */
  @Test
  void sportsAtDefaultsYieldsTheIntervalTrainingExamplesEvents() {
    Map<String, Object> result = run("sports", "{}");
    assertEquals(List.of(
            "Sprint 60..90", "Recovered 141..180",
            "Sprint 180..210", "Recovered 261..300",
            "Sprint 300..330", "Recovered 381..419 truncatedEnd",
            "SessionDone 305..419 truncatedEnd"),
        describe(result, 1000));
    assertEquals(Arrays.asList(null, "51 s", null, "51 s", null, "51 s", null), notes(result));
  }

  @Test
  void itOperationsAtDefaultsYieldsTheDeploymentRegressionExamplesEvents() {
    assertEquals(List.of(
            "Deployment 10..11",
            "ErrorSpike 25..35",
            "Regression 25..35",
            "ErrorSpike 80..85",
            "HighCpu 90..100"),
        describe(run("it-operations", "{}"), 60_000));
  }

  @Test
  void firstRuleAtDefaultsYieldsTheReadmesEvent() {
    assertEquals(List.of("HighTemperature 2000..3000"), events(run("first-rule", "{}")));
  }

  /**
   * The build prerenders every Scenario at its defaults with the compiled module; this puts
   * exactly those calls under the parity test.
   */
  @Test
  void everyScenarioRunsAtItsDefaults() {
    for (Map<String, Object> scenario : scenarios()) {
      Map<String, Object> result = run((String) scenario.get("id"), "{}");
      assertTrue(result.containsKey("events"), scenario.get("id") + ": " + result);
    }
  }

  @Test
  void lowerThresholdStartsAtTheFirstSampleAsTruncated() {
    assertEquals(List.of("HighTemperature 1000..3000 truncatedStart"),
        events(run("first-rule", "{\"threshold\": 70}")));
  }

  @Test
  void thresholdAboveEveryValueYieldsNoEvent() {
    assertEquals(List.of(), events(run("first-rule", "{\"threshold\": 85}")));
  }

  @Test
  void invalidThresholdReturnsRbedsMessage() {
    assertEquals(Map.of("error", "Limit is Infinity"), run("first-rule", "{\"threshold\": 1e999}"));
    assertEquals(Map.of("error", "Limit is NaN"), run("first-rule", "{\"threshold\": null}"));
  }

  @Test
  void lifecycleAtDefaultsConfirmsTwoEventsAfterTwoSecondsAndDiscardsAOneSampleSpike() {
    Map<String, Object> result = run("event-lifecycle", "{}");
    assertEquals(List.of("Overheat 3000..9000", "Overheat 12000..15000"), events(result));
    assertEquals(List.of("5000", "14000"), confirmed(result));
    assertEquals(List.of("Overheat 17000..18000"), discarded(result));
  }

  @Test
  void raisingTheLifecycleDurationPastAShortEventDiscardsIt() {
    Map<String, Object> result = run("event-lifecycle", "{\"duration\": 3}");
    assertEquals(List.of("Overheat 3000..9000"), events(result));
    assertEquals(List.of("6000"), confirmed(result));
    assertEquals(List.of("Overheat 12000..15000", "Overheat 17000..18000"), discarded(result));
  }

  @Test
  void withoutADurationEveryLifecycleEventIsConfirmedAtItsStart() {
    Map<String, Object> result = run("event-lifecycle", "{\"duration\": 0}");
    assertEquals(
        List.of("Overheat 3000..9000", "Overheat 12000..15000", "Overheat 17000..18000"),
        events(result));
    assertEquals(List.of("3000", "12000", "17000"), confirmed(result));
    assertEquals(List.of(), discarded(result));
  }

  @Test
  void negativeLifecycleDurationReturnsRbedsMessage() {
    assertEquals(Map.of("error", "forDuration must be >= 0: -1000"),
        run("event-lifecycle", "{\"duration\": -1}"));
  }

  @Test
  void anEndConditionAtTheStartThresholdLetsTheNoisyWeightFlap() {
    // With an End Condition, `when` is not evaluated while the Event runs: a new Event starts
    // only after the weight has been seen at or below 500 again, so 508 at 6 s starts none.
    assertEquals(List.of("LoadLifted 4000..5000", "LoadLifted 10000..12000",
            "LoadLifted 15000..17000", "LoadLifted 21000..22000"),
        events(run("end-conditions", "{}")));
  }

  @Test
  void movingTheEndThresholdApartLeavesOnlyTheSwingAsASecondEvent() {
    assertEquals(List.of("LoadLifted 4000..14000", "LoadLifted 18000..22000"),
        events(run("end-conditions", "{\"end\": 450}")));
  }

  @Test
  void anEndThresholdBelowTheSwingKeepsOneEvent() {
    assertEquals(List.of("LoadLifted 4000..22000"),
        events(run("end-conditions", "{\"end\": 440}")));
    assertEquals(List.of("LoadLifted 4000..23000"),
        events(run("end-conditions", "{\"end\": 50}")));
  }

  @Test
  void anEndDurationKeepsTheEventAcrossTheSwingAndEndsItWhereTheWeightDropped() {
    assertEquals(List.of("LoadLifted 4000..22000"),
        events(run("end-conditions", "{\"end\": 450, \"endDuration\": 1}")));
  }

  @Test
  void anEndConditionThatNeverHoldsEndsTheEventTruncatedAtClose() {
    assertEquals(List.of("LoadLifted 4000..29000 truncatedEnd"),
        events(run("end-conditions", "{\"end\": 0}")));
  }

  @Test
  void aGapLongerThanMaxGapEndsTheEventAtTheGapAndTruncatesBothSides() {
    assertEquals(List.of("Running 6000..12000 truncatedEnd", "Running 18000..32000 truncatedStart"),
        events(run("data-gaps", "{}")));
  }

  @Test
  void aMaxGapAsLongAsTheGapKeepsTheEventAcrossIt() {
    assertEquals(List.of("Running 6000..32000"),
        events(run("data-gaps", "{\"maxGap\": 6}")));
  }

  @Test
  void aGapOutsideTheEventLeavesItComplete() {
    assertEquals(List.of("Running 6000..32000"),
        events(run("data-gaps", "{\"gap\": 33}")));
  }

  @Test
  void anEventRightAfterAGapHasATruncatedStart() {
    assertEquals(List.of("Running 8000..32000 truncatedStart"),
        events(run("data-gaps", "{\"gap\": 2}")));
  }

  @Test
  void resultHasTheSamples() {
    assertEquals(List.of(
            Map.of("timestamp", 1000.0, "values", Map.of("temp", 75.0)),
            Map.of("timestamp", 2000.0, "values", Map.of("temp", 85.0)),
            Map.of("timestamp", 3000.0, "values", Map.of("temp", 70.0))),
        run("first-rule", "{}").get("samples"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void eachEventCarriesTheAggregatesOfItsRule() {
    List<Map<String, Object>> events =
        (List<Map<String, Object>>) run("first-rule", "{\"threshold\": 70}").get("events");
    assertEquals(Map.of("temp", Map.of("min", 75.0, "max", 85.0, "mean", 80.0, "count", 2.0,
            "timeWeightedMean", 80.0, "integral", 160.0)),
        events.get(0).get("aggregates"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void aggregatesAtDefaultsWeighTheIrregularSamplesByTime() {
    Map<String, Object> result = run("aggregates", "{}");
    assertEquals(List.of("Drive 22000..70000", "Drive 102000..132000"), events(result));
    Map<String, Object> first =
        ((List<Map<String, Object>>) result.get("events")).get(0);
    Map<String, Map<String, Object>> aggregates =
        (Map<String, Map<String, Object>>) first.get("aggregates");
    assertEquals(List.of("speed", "temp"), List.copyOf(aggregates.keySet()));
    Map<String, Object> speed = aggregates.get("speed");
    assertEquals(2.0, speed.get("min"));
    assertEquals(14.0, speed.get("max"));
    assertEquals(112.0 / 12, (Double) speed.get("mean"), 1e-12);
    assertEquals(12.0, speed.get("count"));
    assertEquals(560.0 / 48, (Double) speed.get("timeWeightedMean"), 1e-12);
    assertEquals(560.0, (Double) speed.get("integral"), 1e-12);
  }

  @Test
  void aHigherThresholdShortensTheDrives() {
    assertEquals(List.of("Drive 26000..66000", "Drive 106000..128000"),
        events(run("aggregates", "{\"threshold\": 7}")));
  }

  @Test
  void catalogueNamesThePulseChannels() {
    Map<String, Map<String, Object>> byId = new HashMap<>();
    for (Map<String, Object> s : scenarios()) byId.put((String) s.get("id"), s);
    assertEquals(List.of(), byId.get("first-rule").get("pulse"));
    assertEquals(List.of("deploy"), byId.get("pulse-channels").get("pulse"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void pulseValuesStayAbsentInSamplesThatDoNotCarryThem() {
    List<Map<String, Object>> samples =
        (List<Map<String, Object>>) run("pulse-channels", "{}").get("samples");
    List<Double> deploys = samples.stream()
        .filter(s -> ((Map<String, Object>) s.get("values")).containsKey("deploy"))
        .map(s -> (Double) s.get("timestamp"))
        .toList();
    assertEquals(List.of(600_000.0, 3_600_000.0), deploys);
  }

  @Test
  void pulseChannelsAtDefaultsAttributeOnlyTheFirstErrorSpike() {
    assertEquals(List.of("Deployment 600000..660000", "Regression 1500000..2100000",
            "Deployment 3600000..3660000"),
        events(run("pulse-channels", "{}")));
  }

  @Test
  void aLongerWindowAttributesTheSecondErrorSpikeToo() {
    assertEquals(List.of("Deployment 600000..660000", "Regression 1500000..2100000",
            "Deployment 3600000..3660000", "Regression 6000000..6300000"),
        events(run("pulse-channels", "{\"window\": 45}")));
  }

  @Test
  void duringAtDefaultsHoldsOnlyWhileBothGatingEventsAreConfirmed() {
    Map<String, Object> result = run("during", "{}");
    assertEquals(List.of("Desaturation 100000..160000", "HypoxicTachycardia 110000..160000",
            "Tachycardia 40000..200000", "Desaturation 230000..260000"),
        events(result));
    assertEquals(List.of("110000", "110000", "70000", "240000"), confirmed(result));
    assertEquals(List.of("Desaturation 20000..25000", "Tachycardia 230000..250000"),
        discarded(result));
  }

  @Test
  void aShorterTachycardiaDurationGatesTheLateDesaturationToo() {
    assertEquals(List.of("Desaturation 100000..160000", "HypoxicTachycardia 110000..160000",
            "Tachycardia 40000..200000", "Tachycardia 230000..250000",
            "HypoxicTachycardia 245000..250000", "Desaturation 230000..260000"),
        events(run("during", "{\"tachycardia\": 15}")));
  }

  @Test
  void withoutADesaturationDurationTheCombinationStartsWithTheDip() {
    assertEquals(List.of("Desaturation 20000..25000", "Desaturation 100000..160000",
            "HypoxicTachycardia 100000..160000", "Tachycardia 40000..200000",
            "Desaturation 230000..260000"),
        events(run("during", "{\"desaturation\": 0}")));
  }

  @Test
  void afterAtDefaultsAttributesOnlyTheExcursionSoonAfterTheDoor() {
    assertEquals(List.of("DoorOpen 600000..720000", "ExcursionAfterDoor 900000..1500000",
            "DoorOpen 3600000..3660000"),
        events(run("after", "{}")));
  }

  @Test
  void aWiderAfterWindowAttributesTheLateExcursionUntilTheWindowCloses() {
    assertEquals(List.of("DoorOpen 600000..720000", "ExcursionAfterDoor 900000..1500000",
            "DoorOpen 3600000..3660000", "ExcursionAfterDoor 6000000..6420000"),
        events(run("after", "{\"window\": 45}")));
  }

  @Test
  void aNarrowAfterWindowCutsTheExcursionShort() {
    assertEquals(List.of("DoorOpen 600000..720000", "ExcursionAfterDoor 900000..1080000",
            "DoorOpen 3600000..3660000"),
        events(run("after", "{\"window\": 5}")));
  }

  @Test
  void countAtDefaultsFindsTheFourthStartWithinAnHour() {
    Map<String, Object> result = run("count", "{}");
    assertEquals(List.of("Run 600000..900000", "Run 1800000..2100000", "Run 2700000..3000000",
            "Run 3600000..3900000", "FrequentStarts 3660000..4320000", "Run 9000000..9300000",
            "Run 12000000..12300000"),
        events(result));
    assertEquals(List.of("660000", "1860000", "2760000", "3660000", "3660000", "9060000",
        "12060000"), confirmed(result));
  }

  @Test
  void aShorterCountWindowNeverHoldsFourStarts() {
    assertEquals(List.of(), events(run("count", "{\"window\": 30}")).stream()
        .filter(e -> e.startsWith("FrequentStarts")).toList());
  }

  @Test
  @SuppressWarnings("unchecked")
  void countPlotsTheRunsConfirmedWithinTheWindowAgainstItsLimit() {
    Map<String, Object> plot = (Map<String, Object>) run("count", "{}").get("expression");
    assertEquals("count(run).within(60 min)", plot.get("name"));
    assertEquals(3.0, plot.get("threshold"));
    List<Double> values = (List<Double>) plot.get("values");   // one per minute
    assertEquals(List.of(0.0, 1.0), values.subList(10, 12));      // Run Confirmed at 11
    assertEquals(List.of(1.0, 2.0), values.subList(30, 32));
    assertEquals(List.of(2.0, 3.0), values.subList(45, 47));
    assertEquals(List.of(3.0, 4.0), values.subList(60, 62));      // the fourth Confirmed at 61
    assertEquals(List.of(4.0, 3.0), values.subList(71, 73));      // the first leaves at 72
  }

  @Test
  void countPlotIsAboveItsLimitExactlyWhileFrequentStartsRuns() {
    assertPlotAboveLimitExactlyDuring("count", "{}", "FrequentStarts");
    assertPlotAboveLimitExactlyDuring("count", "{\"window\": 120, \"limit\": 4}", "FrequentStarts");
    assertPlotAboveLimitExactlyDuring("count", "{\"window\": 30, \"limit\": 1}", "FrequentStarts");
  }

  @Test
  void totalDurationAtDefaultsCrossesFortyFiveMinutesInTheThirdExcursionAndStaysAbove() {
    assertEquals(List.of("Excursion 1200000..2400000", "Excursion 4800000..5700000",
            "Excursion 9000000..10500000", "CumulativeLimit 9660000..14400000 truncatedEnd"),
        events(run("total-duration", "{}")));
  }

  @Test
  void aTotalDurationLimitOfAnHourIsNeverExceeded() {
    assertEquals(List.of("Excursion 1200000..2400000", "Excursion 4800000..5700000",
            "Excursion 9000000..10500000"),
        events(run("total-duration", "{\"limit\": 60}")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void totalDurationPlotsTheMinutesSpentInExcursionsAgainstItsLimit() {
    Map<String, Object> plot = (Map<String, Object>) run("total-duration", "{}").get("expression");
    assertEquals("totalDuration(excursion)", plot.get("name"));
    assertEquals("min", plot.get("unit"));
    assertEquals(45.0, plot.get("threshold"));
    List<Double> values = (List<Double>) plot.get("values");   // one per minute
    assertEquals(List.of(0.0, 1.0), values.subList(20, 22));      // grows while one runs
    assertEquals(List.of(20.0, 20.0), values.subList(40, 42));     // and keeps it after
    assertEquals(List.of(35.0, 45.0, 46.0),
        List.of(values.get(150), values.get(160), values.get(161)));  // crossed at 161
    assertEquals(60.0, values.get(240));
  }

  @Test
  void totalDurationPlotIsAboveItsLimitExactlyWhileCumulativeLimitRuns() {
    assertPlotAboveLimitExactlyDuring("total-duration", "{}", "CumulativeLimit");
    assertPlotAboveLimitExactlyDuring("total-duration", "{\"limit\": 30}", "CumulativeLimit");
    assertPlotAboveLimitExactlyDuring("total-duration", "{\"limit\": 0}", "CumulativeLimit");
  }

  @Test
  void latestAtDefaultsComparesEachLiftWithItsOwnMaximum() {
    assertEquals(List.of("PartialSetDown 25000..30000", "LoadLifted 10000..40000",
            "LoadLifted 60000..90000"),
        events(run("latest", "{}")));
  }

  @Test
  void aHigherLatestFractionFindsTheSecondLiftsSetDownToo() {
    assertEquals(List.of("PartialSetDown 25000..30000", "LoadLifted 10000..40000",
            "PartialSetDown 75000..80000", "LoadLifted 60000..90000"),
        events(run("latest", "{\"fraction\": 0.6}")));
  }

  @Test
  void catalogueNamesTheRuleWhoseLatestEventIsRead() {
    Map<String, Map<String, Object>> byId = new HashMap<>();
    for (Map<String, Object> s : scenarios()) byId.put((String) s.get("id"), s);
    assertEquals("LoadLifted", byId.get("latest").get("latest"));
    assertEquals(null, byId.get("first-rule").get("latest"));
  }

  /**
   * The plotted value of a result is above its limit at exactly the Samples where an Event of the
   * Rule that compares it runs: from its start, up to its end, which is included only for a
   * Truncated end, since the Condition still held there.
   */
  @SuppressWarnings("unchecked")
  static void assertPlotAboveLimitExactlyDuring(String scenarioId, String parameters, String rule) {
    Map<String, Object> result = run(scenarioId, parameters);
    Map<String, Object> plot = (Map<String, Object>) result.get("expression");
    List<Double> values = (List<Double>) plot.get("values");
    List<Map<String, Object>> samples = (List<Map<String, Object>>) result.get("samples");
    List<Map<String, Object>> events = ((List<Map<String, Object>>) result.get("events")).stream()
        .filter(e -> rule.equals(e.get("name"))).toList();
    for (int i = 0; i < samples.size(); i++) {
      double t = (Double) samples.get(i).get("timestamp");
      boolean runs = events.stream().anyMatch(e -> (Double) e.get("start") <= t
          && (t < (Double) e.get("end")
              || e.get("truncatedEnd") == Boolean.TRUE && t == (Double) e.get("end")));
      assertEquals(runs, values.get(i) > (Double) plot.get("threshold"), parameters + " at " + t);
    }
  }

  @Test
  void pushedWithTimestampsTheVibrationAcrossTheLostSamplesIsConfirmed() {
    Map<String, Object> result = run("sampling-rate", "{}");
    assertEquals(List.of("Vibration 1500..3500", "Vibration 5000..7000"), events(result));
    assertEquals(List.of("3000", "6500"), confirmed(result));
    assertEquals(List.of(), discarded(result));
  }

  @Test
  void withASamplingRateTheLostSamplesShortenTimeAndTheVibrationIsDiscarded() {
    Map<String, Object> result = run("sampling-rate", "{\"mode\": 1}");
    assertEquals(List.of("Vibration 4100..6100"), events(result));
    assertEquals(List.of("5600"), confirmed(result));
    assertEquals(List.of("Vibration 1500..2600"), discarded(result));
    assertEquals(List.of(2000.0, 2100.0), timestamps(result).subList(20, 22));
  }

  @Test
  void aSlowerDeclaredRateStretchesEveryDuration() {
    Map<String, Object> result = run("sampling-rate", "{\"mode\": 1, \"rate\": 5}");
    assertEquals(List.of("Vibration 3000..5200", "Vibration 8200..12200"), events(result));
    assertEquals(List.of("4600", "9800"), confirmed(result));
  }

  @Test
  @SuppressWarnings("unchecked")
  void advancingTimeThroughTheSilenceEndsTheHighHeartRateAndConfirmsSensorLost() {
    Map<String, Object> result = run("silent-sensors", "{}");
    assertEquals(List.of("HighHeartRate 12000..30000", "SensorLost 30000..45000"),
        events(result));
    List<Double> advances = (List<Double>) result.get("advances");
    assertEquals(20, advances.size());
    assertEquals(List.of(25000.0, 44000.0), List.of(advances.get(0), advances.get(19)));
    assertEquals(List.of(24000.0, 45000.0), timestamps(result).subList(24, 26));
  }

  @Test
  void withoutAdvancesTheSilenceGoesUnnoticed() {
    Map<String, Object> result = run("silent-sensors", "{\"timer\": 1}");
    assertEquals(List.of("HighHeartRate 12000..45000"), events(result));
    assertEquals(List.of(), result.get("advances"));
  }

  @Test
  void aLongerSilenceLimitNoticesTheSilenceLater() {
    assertEquals(List.of("HighHeartRate 12000..35000", "SensorLost 35000..45000"),
        events(run("silent-sensors", "{\"limit\": 10}")));
  }

  static final long DAY = 86_400_000;

  @Test
  void withHistoryTheRestartedDetectorKnowsTheLastMaintenance() {
    Map<String, Object> result = run("history", "{}");
    assertEquals(List.of("Maintenance 10..11", "OverdueService 0..11 truncatedStart",
            "Maintenance 140..141", "OverdueService 102..141"),
        describe(result, DAY));
    assertEquals(60.0 * DAY, result.get("restart"));
  }

  @Test
  void withoutHistoryTheRestartedDetectorFindsTheServiceOverdueAtOnce() {
    assertEquals(List.of("Maintenance 10..11", "OverdueService 0..11 truncatedStart",
            "Maintenance 140..141", "OverdueService 60..141 truncatedStart"),
        describe(run("history", "{\"history\": 1}"), DAY));
  }

  @Test
  void aRestartMoreThanNinetyDaysAfterTheMaintenanceNeedsNoHistory() {
    List<String> expected = List.of("Maintenance 10..11", "OverdueService 0..11 truncatedStart",
        "OverdueService 102..119 truncatedEnd", "Maintenance 140..141",
        "OverdueService 120..141 truncatedStart");
    assertEquals(expected, describe(run("history", "{\"restart\": 120}"), DAY));
    assertEquals(expected, describe(run("history", "{\"restart\": 120, \"history\": 1}"), DAY));
  }

  @SuppressWarnings("unchecked")
  static List<Double> timestamps(Map<String, Object> result) {
    return ((List<Map<String, Object>>) result.get("samples")).stream()
        .map(s -> (Double) s.get("timestamp"))
        .toList();
  }

  @Test
  void catalogueHasTheFirstRuleWithItsThresholdAndSource() {
    Map<String, Object> first = scenarios().get(0);
    assertEquals("first-rule", first.get("id"));
    assertEquals(List.of("temp"), first.get("channels"));
    assertEquals(List.of(Map.of("id", "threshold", "label", "Threshold", "default", 80.0,
        "min", 60.0, "max", 90.0, "step", 1.0, "unit", "°C")), first.get("parameters"));
    assertTrue(((String) first.get("source"))
        .contains("Rule.named(\"HighTemperature\").when(temp.above(${threshold}))"));
  }

  @Test
  void catalogueNamesTheRulesAndShowsTheFirstRuleInSeconds() {
    Map<String, Object> first = scenarios().get(0);
    assertEquals(List.of("HighTemperature"), first.get("rules"));
    assertEquals(Map.of("name", "s", "millis", 1000.0), first.get("timeUnit"));
  }

  /** The Expression's values in a result, null where it is undefined. */
  @SuppressWarnings("unchecked")
  static List<Double> expressionValues(Map<String, Object> result) {
    return (List<Double>) ((Map<String, Object>) result.get("expression")).get("values");
  }

  @Test
  void movingAverageOverFiveSamplesIsUndefinedForTheFirstFour() {
    List<Double> values =
        expressionValues(run("expressions", "{\"expression\": 1, \"samples\": 5}"));
    assertEquals(Arrays.asList(null, null, null, null), values.subList(0, 4));
    assertEquals(26.0, values.get(4), 1e-12); // mean of 26, 26.2, 25.9, 26.1, 25.8
  }

  @Test
  void noEventStartsWhileTheMovingAverageWarmsUp() {
    // temp is above 24 from the first Sample, but the average is undefined until 4 s.
    assertEquals(
        List.of("AboveLimit 4000..7000 truncatedStart", "AboveLimit 37000..59000 truncatedEnd"),
        events(run("expressions", "{}")));
  }

  /** The number of Samples before the Expression is first defined. */
  static long warmUp(Map<String, Object> result) {
    return expressionValues(result).stream().takeWhile(v -> v == null).count();
  }

  @Test
  void rateIsDefinedFromTheSecondValueAndSeesTheOutlier() {
    Map<String, Object> result = run("expressions", "{\"expression\": 0}");
    assertEquals(1, warmUp(result));
    assertEquals(8.2, expressionValues(result).get(15), 1e-12); // 28 after 19.8, one second apart
    assertEquals(List.of("AboveLimit 15000..16000", "AboveLimit 31000..39000"), events(result));
  }

  @Test
  void movingAverageOverADurationIsUndefinedUntilItsValuesSpanTheWindow() {
    assertEquals(10, warmUp(run("expressions", "{\"expression\": 2}")));
    assertEquals(5, warmUp(run("expressions", "{\"expression\": 2, \"window\": 5}")));
  }

  @Test
  void differenceOfTwoChannelsIsUndefinedUntilBothHaveAValue() {
    Map<String, Object> result = run("expressions", "{\"expression\": 3}");
    assertEquals(5, warmUp(result));
    assertEquals(4.1, expressionValues(result).get(5), 1e-12); // 26.1 - 22
    assertEquals(List.of("AboveLimit 15000..16000", "AboveLimit 38000..59000 truncatedEnd"),
        events(result));
  }

  @Test
  void anExpressionThatIsNoOptionReturnsAnError() {
    assertEquals(Map.of("error", "Parameter expression has no option 4.0"),
        run("expressions", "{\"expression\": 4}"));
    assertEquals(Map.of("error", "Parameter expression has no option 0.5"),
        run("expressions", "{\"expression\": 0.5}"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void catalogueListsTheExpressionsAsOptionsWithTheirSource() {
    Map<String, Object> scenario = scenarios().stream()
        .filter(s -> s.get("id").equals("expressions")).findFirst().orElseThrow();
    Map<String, Object> expression =
        ((List<Map<String, Object>>) scenario.get("parameters")).get(0);
    assertEquals("expression", expression.get("id"));
    assertEquals(1.0, expression.get("default"));
    assertEquals(Map.of("label", "Moving average over Samples",
            "source", "temp.averageOverSamples(${samples}).above(24)"),
        ((List<Object>) expression.get("options")).get(1));
  }

  @Test
  void aScenarioWithoutAnExpressionHasNoneInItsResult() {
    assertFalse(run("first-rule", "{}").containsKey("expression"));
  }

  @Test
  void aDrawnChannelReplacesTheScenariosValues() {
    Map<String, Object> result = run("first-rule", "{}", "{\"temp\": [85, 70, 75]}");
    assertEquals(List.of("HighTemperature 1000..2000 truncatedStart"), events(result));
    assertEquals(List.of(
            Map.of("timestamp", 1000.0, "values", Map.of("temp", 85.0)),
            Map.of("timestamp", 2000.0, "values", Map.of("temp", 70.0)),
            Map.of("timestamp", 3000.0, "values", Map.of("temp", 75.0))),
        result.get("samples"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void onlyTheDrawnChannelsChange() {
    String stopped = "{\"speed\": [" + "0,".repeat(28) + "0]}";
    Map<String, Object> result = run("aggregates", "{}", stopped);
    assertEquals(List.of(), events(result));
    Map<String, Object> last = ((List<Map<String, Object>>) result.get("samples")).get(28);
    assertEquals(Map.of("speed", 0.0, "temp", 73.0), last.get("values"));
  }

  @Test
  void aNullDrawnValueLeavesTheChannelAbsentInThatSample() {
    Map<String, Object> result = run("first-rule", "{}", "{\"temp\": [null, 90, 70]}");
    assertEquals(Map.of("timestamp", 1000.0, "values", Map.of()),
        ((List<?>) result.get("samples")).get(0));
    // temp is unknown before 2000, so rbed cannot tell whether the Event began earlier.
    assertEquals(List.of("HighTemperature 2000..3000 truncatedStart"), events(result));
  }

  @Test
  void drawnValuesMustMatchTheScenariosChannelsAndSamples() {
    assertEquals(Map.of("error", "No Channel pressure in Scenario first-rule"),
        run("first-rule", "{}", "{\"pressure\": [1, 2, 3]}"));
    assertEquals(Map.of("error", "Drawn Channel temp has 2 values for 3 Samples"),
        run("first-rule", "{}", "{\"temp\": [1, 2]}"));
    assertEquals(Map.of("error", "Drawn Channel temp has a value that is not a number"),
        run("first-rule", "{}", "{\"temp\": [1, \"x\", 3]}"));
  }
}
