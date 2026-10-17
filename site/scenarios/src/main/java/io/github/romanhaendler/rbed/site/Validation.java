package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.during;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's Errors and validation: a room's temperature, one Sample a second, warm from 10 to
 * 29 s. Its controls reach rbed's own rejections: an average over more Samples than the Retention
 * Limit and a Rule added before the Rule it refers to ({@code build()}), a Retention Limit below
 * one ({@code retentionLimit}) and a negative duration ({@code forDuration}).
 */
final class Validation extends Scenario {
  static final Channel TEMP = Channel.named("temp");

  Validation() {
    super("validation", "Errors and validation", "s", 1_000, List.of(TEMP),
        List.of(
            new Parameter("window", "Average over", 5, 1, 12, 1, "Samples"),
            new Parameter("retention", "Retention Limit", 10, 0, 10, 1, "entries"),
            new Parameter("duration", "Warm for", 2, -1, 5, 1, "s"),
            Parameter.choice("order", "Rules added", 0, List.of(
                new Option("Warm, then Hot", ".rules(warm, hot)"),
                new Option("Hot, then Warm", ".rules(hot, warm)")))),
        """
        import static io.github.romanhaendler.rbed.Conditions.*;

        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.Map;

        Channel temp = Channel.named("temp");

        Rule warm = Rule.named("Warm")
            .when(temp.averageOverSamples(${window}).above(25))
            .forDuration(Duration.ofSeconds(${duration}));
        Rule hot = Rule.named("Hot").when(during(warm).and(temp.above(27)));
        Detector detector = Detector.builder()
            .retentionLimit(${retention})
            ${order}
            .build();

        for (int s = 0; s <= 40; s++) {   // one Sample a second
          detector.push(s * 1_000L, Map.of(temp, s >= 10 && s < 30 ? 28.0 : 22.0));
        }
        detector.close();
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int s = 0; s <= 40; s++) {
      samples.add(new Sample(s * 1_000L, Map.of(TEMP, s >= 10 && s < 30 ? 28.0 : 22.0)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule warm = Rule.named("Warm")
        .when(TEMP.averageOverSamples((int) Math.round(values.get("window"))).above(25))
        .forDuration(Math.round(values.get("duration") * 1_000));
    Rule hot = Rule.named("Hot").when(during(warm).and(TEMP.above(27)));
    return values.get("order") == 0 ? new Rule[] {warm, hot} : new Rule[] {hot, warm};
  }

  /** Builds the Detector with the chosen Retention Limit, then pushes every Sample. */
  @Override
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    Detector detector = lifecycle.builder()
        .retentionLimit((int) Math.round(values.get("retention")))
        .rules(rules)
        .build();
    for (Sample sample : samples) lifecycle.push(detector, sample);
    detector.close();
  }

  @Override
  List<Plot> plots(Map<String, Double> values) {
    int window = (int) Math.round(values.get("window"));
    return List.of(new Plot("temp.averageOverSamples(" + window + ")",
        TEMP.averageOverSamples(window), "Warm", "when >", 25));
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(TEMP, "Hot", "when >", 27));
  }
}
