package io.github.romanhaendler.rbed.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
  void pushedWithTimestampsTheVibrationAcrossTheLostSamplesIsConfirmed() {
    Map<String, Object> result = run("sampling-rate", "{}");
    assertEquals(List.of("Vibration 1500..3500", "Vibration 5000..7000"), events(result));
    assertEquals(List.of("3000", "6500"), confirmed(result));
    assertEquals(List.of(), discarded(result));
  }

  @Test
  void withASamplingRateTheLostSamplesShortenTimeAndTheVibrationIsDiscarded() {
    Map<String, Object> result = run("sampling-rate", "{\"samplingRate\": 1}");
    assertEquals(List.of("Vibration 4100..6100"), events(result));
    assertEquals(List.of("5600"), confirmed(result));
    assertEquals(List.of("Vibration 1500..2600"), discarded(result));
    assertEquals(List.of(2000.0, 2100.0), timestamps(result).subList(20, 22));
  }

  @Test
  void aSlowerDeclaredRateStretchesEveryDuration() {
    Map<String, Object> result = run("sampling-rate", "{\"samplingRate\": 1, \"rate\": 5}");
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
    Map<String, Object> result = run("silent-sensors", "{\"advance\": 0}");
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
        describe(run("history", "{\"history\": 0}"), DAY));
  }

  @Test
  void aRestartMoreThanNinetyDaysAfterTheMaintenanceNeedsNoHistory() {
    List<String> expected = List.of("Maintenance 10..11", "OverdueService 0..11 truncatedStart",
        "OverdueService 102..119 truncatedEnd", "Maintenance 140..141",
        "OverdueService 120..141 truncatedStart");
    assertEquals(expected, describe(run("history", "{\"restart\": 120}"), DAY));
    assertEquals(expected, describe(run("history", "{\"restart\": 120, \"history\": 0}"), DAY));
  }

  @Test
  void catalogueMarksToggles() {
    Map<String, Map<String, Object>> byId = new HashMap<>();
    for (Map<String, Object> s : scenarios()) byId.put((String) s.get("id"), s);
    assertEquals(List.of(Map.of("id", "samplingRate", "label", "Sampling rate mode",
            "default", 0.0, "min", 0.0, "max", 1.0, "step", 1.0, "unit", "", "toggle", true),
        Map.of("id", "rate", "label", "Declared rate", "default", 10.0, "min", 1.0, "max", 20.0,
            "step", 1.0, "unit", "Hz")), byId.get("sampling-rate").get("parameters"));
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
}
