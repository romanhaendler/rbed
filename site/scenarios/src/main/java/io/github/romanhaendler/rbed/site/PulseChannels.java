package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.after;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The README's Pulse Channels: a service's errors per minute and a Pulse Channel reporting its
 * deployments, at minutes 10 and 60. Regression is an error spike within a window after a
 * Deployment; errors spike at minutes 25 to 35 and 100 to 105.
 */
final class PulseChannels extends Scenario {
  static final Channel DEPLOY = Channel.pulse("deploy");
  static final Channel ERRORS = Channel.named("errors");
  static final long MINUTE = 60_000;

  PulseChannels() {
    super("pulse-channels", "Pulse Channels", "min", MINUTE, List.of(ERRORS, DEPLOY),
        List.of(
            new Parameter("window", "Window after a Deployment", 30, 5, 60, 5, "min"),
            new Parameter("limit", "Error limit", 5, 0, 15, 1, "errors/min")),
        """
        import static io.github.romanhaendler.rbed.Conditions.*;

        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.HashMap;
        import java.util.Map;

        Channel deploy = Channel.pulse("deploy");
        Channel errors = Channel.named("errors");

        Rule deployment = Rule.named("Deployment").when(deploy.above(0.5));
        Rule regression = Rule.named("Regression")
            .when(after(deployment).within(Duration.ofMinutes(${window})).and(errors.above(${limit})));
        Detector detector = Detector.builder()
            .rules(deployment, regression)
            .build();

        for (int m = 0; m <= 120; m++) {   // one Sample a minute
          Map<Channel, Double> values = new HashMap<>();
          values.put(errors, m >= 25 && m < 35 ? 12.0 : m >= 100 && m < 105 ? 9.0 : 3.0);
          if (m == 10 || m == 60) values.put(deploy, 1.0);   // only the Samples with a deployment carry deploy
          detector.push(m * 60_000L, values);
        }
        detector.close();
        """);
  }

  @Override
  List<Channel> pulseChannels() {
    return List.of(DEPLOY);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int m = 0; m <= 120; m++) {
      Map<Channel, Double> sample = new HashMap<>();
      sample.put(ERRORS, m >= 25 && m < 35 ? 12.0 : m >= 100 && m < 105 ? 9.0 : 3.0);
      if (m == 10 || m == 60) sample.put(DEPLOY, 1.0);
      samples.add(new Sample(m * MINUTE, sample));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule deployment = Rule.named("Deployment").when(DEPLOY.above(0.5));
    Rule regression = Rule.named("Regression")
        .when(after(deployment).within((long) (values.get("window") * MINUTE))
            .and(ERRORS.above(values.get("limit"))));
    return new Rule[] {deployment, regression};
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(ERRORS, "Regression", "when >", values.get("limit")));
  }

  @Override
  List<Window> windows(Map<String, Double> values) {
    return List.of(new Window("Deployment", (long) (values.get("window") * MINUTE)));
  }
}
