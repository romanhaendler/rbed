package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.after;
import static rbed.examples.Samples.MINUTE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Channel;
import rbed.Detector;
import rbed.Event;
import rbed.Rule;

/**
 * IT operations: per-minute metrics of a web service (errors per minute, CPU %) and a Channel
 * reporting deployments, declared as a Pulse Channel so it is only set in the Sample carrying it.
 *
 * <ul>
 *   <li>Deployment: a deployment is reported.
 *   <li>ErrorSpike: more than 5 errors per minute for 2 minutes.
 *   <li>Regression: an error spike within 30 minutes after a Deployment, i.e. probably caused by
 *       it.
 *   <li>HighCpu: CPU above 90 % for 5 minutes.
 * </ul>
 */
class DeploymentRegressionExample {
  static final Channel CPU = Channel.named("cpu");
  static final Channel DEPLOY = Channel.pulse("deploy");
  static final Channel ERRORS = Channel.named("errors");

  static double errors(double m) {
    if (m >= 25 && m < 35) return 12;
    if (m >= 80 && m < 85) return 9;
    return 1;
  }

  @Test
  void attributesErrorSpikesToDeployments() {
    Rule deployment = Rule.named("Deployment").when(DEPLOY.above(0.5));
    Detector detector = Detector.builder()
        .rules(deployment)
        .rules(Rule.named("ErrorSpike").when(ERRORS.above(5)).forDuration(2 * MINUTE))
        .rules(Rule.named("Regression")
            .when(after(deployment).within(30 * MINUTE).and(ERRORS.above(5)))
            .forDuration(2 * MINUTE))
        .rules(Rule.named("HighCpu").when(CPU.above(90)).forDuration(5 * MINUTE))
        .build();

    List<Event> events = detector.detect(Samples.every(1, 0, 120, MINUTE, m -> {
      Map<Channel, Double> values = new HashMap<>(Map.of(
          ERRORS, errors(m),
          CPU, m >= 90 && m < 100 ? 95.0 : 50.0));
      if (m == 10) values.put(DEPLOY, 1.0); // sent once, never reset
      return values;
    }));

    assertEquals(List.of(
            "Deployment 10..11",
            "ErrorSpike 25..35",
            "Regression 25..35",   // 14 minutes after the Deployment
            "ErrorSpike 80..85",   // too long after it: no Regression
            "HighCpu 90..100"),
        Samples.describe(events, MINUTE));
  }
}
