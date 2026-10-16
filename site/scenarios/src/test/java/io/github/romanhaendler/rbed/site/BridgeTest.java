package io.github.romanhaendler.rbed.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    Map<String, Object> expression =
        ((List<Map<String, Object>>) scenarios().get(1).get("parameters")).get(0);
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
}
