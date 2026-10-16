package io.github.romanhaendler.rbed.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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
  void resultHasTheSamples() {
    assertEquals(List.of(
            Map.of("timestamp", 1000.0, "values", Map.of("temp", 75.0)),
            Map.of("timestamp", 2000.0, "values", Map.of("temp", 85.0)),
            Map.of("timestamp", 3000.0, "values", Map.of("temp", 70.0))),
        run("first-rule", "{}").get("samples"));
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
