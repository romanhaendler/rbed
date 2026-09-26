package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.after;
import static rbed.Conditions.channel;
import static rbed.examples.Series.MINUTE;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Detector;
import rbed.Event;

/**
 * IT operations: per-minute metrics of a web service (errors per minute, CPU %) and a marker
 * Channel for deployments.
 *
 * <ul>
 *   <li>Deployment: the marker is set.
 *   <li>ErrorSpike: more than 5 errors per minute for 2 minutes.
 *   <li>Regression: an error spike within 30 minutes after a Deployment, i.e. probably caused by
 *       it.
 *   <li>HighCpu: CPU above 90 % for 5 minutes.
 * </ul>
 *
 * <p>Gap: Channels hold their last value, so a marker must be reset explicitly (deploy = 0 on the
 * next Sample), or it would read as "deploying" forever. Pulse Channels, whose value only exists
 * in the Sample that carries it, are not supported.
 */
class DeploymentRegressionExample {

  static double errors(double m) {
    if (m >= 25 && m < 35) return 12;
    if (m >= 80 && m < 85) return 9;
    return 1;
  }

  @Test
  void attributesErrorSpikesToDeployments() {
    Detector detector = Detector.builder()
        .rule("Deployment", r -> r.when(channel("deploy").above(0.5)))
        .rule("ErrorSpike", r -> r.when(channel("errors").above(5)).forDuration(2 * MINUTE))
        .rule("Regression", r -> r
            .when(after("Deployment").within(30 * MINUTE).and(channel("errors").above(5)))
            .forDuration(2 * MINUTE))
        .rule("HighCpu", r -> r.when(channel("cpu").above(90)).forDuration(5 * MINUTE))
        .build();

    List<Event> events = detector.detect(Series.every(1, 0, 120, MINUTE, m -> Map.of(
        "deploy", m == 10 ? 1.0 : 0.0,
        "errors", errors(m),
        "cpu", m >= 90 && m < 100 ? 95.0 : 50.0)));

    assertEquals(List.of(
            "Deployment 10..11",
            "ErrorSpike 25..35",
            "Regression 25..35",   // 14 minutes after the Deployment
            "ErrorSpike 80..85",   // too long after it: no Regression
            "HighCpu 90..100"),
        Series.describe(events, MINUTE));
  }
}
