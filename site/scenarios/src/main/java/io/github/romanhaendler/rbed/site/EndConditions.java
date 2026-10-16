package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's LoadLifted with an End Condition: a crane's load cell, lifting a load whose weight
 * reads around 500 kg as it swings. With the End Condition at the start threshold the Rule
 * flaps; an End Condition further down, or a duration on it, keeps one Event.
 */
final class EndConditions extends Scenario {
  static final Channel WEIGHT = Channel.named("weight");
  /** weight at 0, 1, 2, ... s: lifted at 3 s, a deep swing at 14 s, set down at 22 s. */
  private static final double[] VALUES = {
    0, 0, 0, 260, 515, 492, 508, 521, 489, 497, 512, 506, 494, 518, 440,
    510, 503, 485, 514, 509, 496, 517, 280, 30, 0, 0, 0, 0, 0, 0
  };

  EndConditions() {
    super("end-conditions", "End Conditions and hysteresis", "s", 1_000, List.of(WEIGHT),
        List.of(
            new Parameter("end", "End threshold", 500, 0, 500, 10, "kg"),
            new Parameter("endDuration", "End duration", 0, 0, 5, 1, "s")),
        """
        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.Map;

        Channel weight = Channel.named("weight");

        Rule loadLifted = Rule.named("LoadLifted")
            .when(weight.above(500))
            .endWhen(weight.below(${end})).forDuration(Duration.ofSeconds(${endDuration}));
        Detector detector = Detector.builder()
            .rules(loadLifted)
            .build();

        double[] values = {0, 0, 0, 260, 515, 492, 508, 521, 489, 497, 512, 506, 494, 518, 440,
            510, 503, 485, 514, 509, 496, 517, 280, 30, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < values.length; i++) {
          detector.push(i * 1_000L, Map.of(weight, values[i]));   // one Sample per second
        }
        detector.close();
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int i = 0; i < VALUES.length; i++) {
      samples.add(new Sample(i * 1_000L, Map.of(WEIGHT, VALUES[i])));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    return new Rule[] {
      Rule.named("LoadLifted").when(WEIGHT.above(500))
          .endWhen(WEIGHT.below(values.get("end")))
          .forDuration(Math.round(values.get("endDuration") * 1_000))
    };
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    double end = values.get("end");
    return end == 500
        ? List.of(new Threshold(WEIGHT, "LoadLifted", "when > and endWhen <", 500))
        : List.of(new Threshold(WEIGHT, "LoadLifted", "when >", 500),
            new Threshold(WEIGHT, "LoadLifted", "endWhen <", end));
  }
}
