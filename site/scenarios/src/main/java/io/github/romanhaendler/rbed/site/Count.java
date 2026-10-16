package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.count;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's FrequentStarts: a machine's Runs (power above 500 W for 30 s) start at minutes
 * 10, 30, 45, 60, 150 and 200 and last five minutes each. The fourth start within an hour is
 * frequent.
 */
final class Count extends Scenario {
  static final Channel POWER = Channel.named("power");
  static final long MINUTE = 60_000;
  private static final int[] STARTS = {10, 30, 45, 60, 150, 200};

  Count() {
    super("count", "count", "min", MINUTE, List.of(POWER),
        List.of(
            new Parameter("window", "Window", 60, 10, 120, 10, "min"),
            new Parameter("limit", "Limit", 3, 0, 6, 1, "Runs")),
        """
        Channel power = Channel.named("power");

        Rule run = Rule.named("Run").when(power.above(500)).forDuration(30_000);
        Rule frequentStarts = Rule.named("FrequentStarts")
            .when(count(run).within(Duration.ofMinutes(${window})).above(${limit}));
        Detector detector = Detector.builder()
            .rules(run, frequentStarts)
            .build();

        detector.push(0,      Map.of(power, 0.0));   // one Sample a minute
        detector.push(60_000, Map.of(power, 0.0));
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int m = 0; m <= 240; m++) {
      double power = 0;
      for (int start : STARTS) {
        if (m >= start && m < start + 5) power = 800;
      }
      samples.add(new Sample(m * MINUTE, Map.of(POWER, power)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule run = Rule.named("Run").when(POWER.above(500)).forDuration(30_000);
    Rule frequentStarts = Rule.named("FrequentStarts")
        .when(count(run).within(Math.round(values.get("window") * MINUTE))
            .above(values.get("limit")));
    return new Rule[] {run, frequentStarts};
  }

  @Override
  Plot plot(Map<String, Double> values) {
    long window = Math.round(values.get("window") * MINUTE);
    return new Plot("count(run).within(" + window / MINUTE + " min)",
        count(rules(values)[0]).within(window), values.get("limit"), "Runs");
  }
}
