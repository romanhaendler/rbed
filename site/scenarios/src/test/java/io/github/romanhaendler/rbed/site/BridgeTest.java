package io.github.romanhaendler.rbed.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    String result = Bridge.run(scenarioId, parameters, "{}");
    return (Map<String, Object>) Json.parse(call("run", result, scenarioId, parameters, "{}"));
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
    assertEquals(List.of("Run 600000..900000", "Run 1800000..2100000", "Run 2700000..3000000",
            "Run 3600000..3900000", "FrequentStarts 3660000..4320000", "Run 9000000..9300000",
            "Run 12000000..12300000"),
        events(run("count", "{}")));
  }

  @Test
  void aShorterCountWindowNeverHoldsFourStarts() {
    assertEquals(List.of(), events(run("count", "{\"window\": 30}")).stream()
        .filter(e -> e.startsWith("FrequentStarts")).toList());
  }

  @Test
  @SuppressWarnings("unchecked")
  void countPlotsTheRunsConfirmedWithinTheWindowAgainstItsLimit() {
    Map<String, Object> running = (Map<String, Object>) run("count", "{}").get("running");
    assertEquals("count(run).within(60 min)", running.get("label"));
    assertEquals(3.0, running.get("limit"));
    List<Double> values = (List<Double>) running.get("values");   // one per minute
    assertEquals(List.of(0.0, 1.0), values.subList(10, 12));      // Run Confirmed at 11
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
    Map<String, Object> running = (Map<String, Object>) run("total-duration", "{}").get("running");
    assertEquals("totalDuration(excursion)", running.get("label"));
    assertEquals("min", running.get("unit"));
    assertEquals(45.0, running.get("limit"));
    List<Double> values = (List<Double>) running.get("values");   // one per minute
    assertEquals(List.of(0.0, 1.0), values.subList(20, 22));      // grows while one runs
    assertEquals(List.of(20.0, 20.0), values.subList(40, 42));     // and keeps it after
    assertEquals(List.of(35.0, 45.0, 46.0), List.of(values.get(150), values.get(160), values.get(161)));
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
    Map<String, Object> running = (Map<String, Object>) result.get("running");
    List<Double> values = (List<Double>) running.get("values");
    List<Map<String, Object>> samples = (List<Map<String, Object>>) result.get("samples");
    List<Map<String, Object>> events = ((List<Map<String, Object>>) result.get("events")).stream()
        .filter(e -> rule.equals(e.get("name"))).toList();
    for (int i = 0; i < samples.size(); i++) {
      double t = (Double) samples.get(i).get("timestamp");
      boolean runs = events.stream().anyMatch(e -> (Double) e.get("start") <= t
          && (t < (Double) e.get("end") || e.get("truncatedEnd") == Boolean.TRUE && t == (Double) e.get("end")));
      assertEquals(runs, values.get(i) > (Double) running.get("limit"), parameters + " at " + t);
    }
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
}
