package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.during;
import static io.github.romanhaendler.rbed.Conditions.latest;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's PartialSetDown: a crane lifts 800 kg from 10 to 40 s and sets it down to 300 kg
 * from 25 to 30 s, then lifts 600 kg from 60 to 90 s and sets it down to 350 kg from 75 to 80 s.
 * 350 is below half of the first lift, but not of the second, which is the one latest reads.
 */
final class Latest extends Scenario {
  static final Channel WEIGHT = Channel.named("weight");

  Latest() {
    super("latest", "latest", "s", 1_000, List.of(WEIGHT),
        List.of(new Parameter("fraction", "Fraction of the maximum", 0.5, 0.1, 0.9, 0.05, "")),
        """
        Channel weight = Channel.named("weight");

        Rule loadLifted = Rule.named("LoadLifted").when(weight.above(500)).endWhen(weight.below(50));
        Rule partialSetDown = Rule.named("PartialSetDown")
            .when(during(loadLifted).and(weight.below(latest(loadLifted).max(weight).times(${fraction}))));
        Detector detector = Detector.builder()
            .rules(loadLifted, partialSetDown)
            .build();

        detector.push(0,     Map.of(weight, 0.0));   // one Sample a second
        detector.push(1_000, Map.of(weight, 0.0));
        """);
  }

  @Override
  String readsLatest() {
    return "LoadLifted";
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int s = 0; s <= 110; s++) {
      double weight = s >= 25 && s < 30 ? 300
          : s >= 10 && s < 40 ? 800
          : s >= 75 && s < 80 ? 350
          : s >= 60 && s < 90 ? 600
          : 0;
      samples.add(new Sample(s * 1_000L, Map.of(WEIGHT, weight)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule loadLifted = Rule.named("LoadLifted").when(WEIGHT.above(500)).endWhen(WEIGHT.below(50));
    Rule partialSetDown = Rule.named("PartialSetDown")
        .when(during(loadLifted)
            .and(WEIGHT.below(latest(loadLifted).max(WEIGHT).times(values.get("fraction")))));
    return new Rule[] {loadLifted, partialSetDown};
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(WEIGHT, "LoadLifted", "when >", 500),
        new Threshold(WEIGHT, "LoadLifted", "endWhen <", 50));
  }
}
