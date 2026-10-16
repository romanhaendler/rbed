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
 * DeploymentRegressionExample: a web service's errors per minute and CPU %, and a Pulse Channel
 * reporting a deployment at minute 10.
 */
final class ItOperations extends Scenario {
  static final Channel CPU = Channel.named("cpu");
  static final Channel DEPLOY = Channel.pulse("deploy");
  static final Channel ERRORS = Channel.named("errors");
  static final long MINUTE = 60_000;

  ItOperations() {
    super("it-operations", "IT operations", "min", MINUTE, List.of(ERRORS, CPU, DEPLOY),
        List.of(
            new Parameter("errors", "Error spike above", 5, 0, 15, 1, "errors/min"),
            new Parameter("spikeMinutes", "Error spike for", 2, 0, 10, 1, "min"),
            new Parameter("window", "Regression within", 30, 5, 90, 5, "min"),
            new Parameter("cpu", "High CPU above", 90, 50, 100, 5, "%"),
            new Parameter("cpuMinutes", "High CPU for", 5, 1, 15, 1, "min")),
        """
        Channel cpu = Channel.named("cpu");
        Channel deploy = Channel.pulse("deploy");
        Channel errors = Channel.named("errors");
        long MINUTE = 60_000;

        Rule deployment = Rule.named("Deployment").when(deploy.above(0.5));
        Rule errorSpike = Rule.named("ErrorSpike")
            .when(errors.above(${errors})).forDuration(${spikeMinutes} * MINUTE);
        Rule regression = Rule.named("Regression")
            .when(after(deployment).within(${window} * MINUTE).and(errors.above(${errors})))
            .forDuration(${spikeMinutes} * MINUTE);
        Rule highCpu = Rule.named("HighCpu")
            .when(cpu.above(${cpu})).forDuration(${cpuMinutes} * MINUTE);
        Detector detector = Detector.builder()
            .rules(deployment, errorSpike, regression, highCpu)
            .build();
        """);
  }

  @Override
  List<Channel> pulseChannels() {
    return List.of(DEPLOY);
  }

  static double errors(int m) {
    if (m >= 25 && m < 35) return 12;
    if (m >= 80 && m < 85) return 9;
    return 1;
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> series = new ArrayList<>();
    for (int m = 0; m < 120; m++) {
      Map<Channel, Double> sample = new HashMap<>(Map.of(
          ERRORS, errors(m),
          CPU, m >= 90 && m < 100 ? 95.0 : 50.0));
      if (m == 10) sample.put(DEPLOY, 1.0); // sent once, never reset
      series.add(new Sample(m * MINUTE, sample));
    }
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    double errors = values.get("errors");
    long spike = Math.round(values.get("spikeMinutes") * MINUTE);
    Rule deployment = Rule.named("Deployment").when(DEPLOY.above(0.5));
    Rule errorSpike = Rule.named("ErrorSpike").when(ERRORS.above(errors)).forDuration(spike);
    Rule regression = Rule.named("Regression")
        .when(after(deployment).within(Math.round(values.get("window") * MINUTE))
            .and(ERRORS.above(errors)))
        .forDuration(spike);
    Rule highCpu = Rule.named("HighCpu")
        .when(CPU.above(values.get("cpu")))
        .forDuration(Math.round(values.get("cpuMinutes") * MINUTE));
    return new Rule[] {deployment, errorSpike, regression, highCpu};
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(ERRORS, "ErrorSpike, Regression", "when >", values.get("errors")),
        new Threshold(CPU, "HighCpu", "when >", values.get("cpu")));
  }

  @Override
  List<Window> windows(Map<String, Double> values) {
    return List.of(new Window("Deployment", Math.round(values.get("window") * MINUTE)));
  }
}
