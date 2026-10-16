package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Expression;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's Conditions and Expressions: one Rule compares an Expression of a noisy temperature
 * with a limit, and the reader picks the Expression: a rate, a moving average over Samples or over
 * a duration, or the difference to a second Channel. The temperature starts warm, has a single
 * outlier at 15 s and ramps up from 30 s; the ambient temperature reports only from 5 s on.
 */
final class Expressions extends Scenario {
  static final Channel TEMP = Channel.named("temp");
  static final Channel AMBIENT = Channel.named("ambient");
  private static final double[] NOISE = {0.0, 0.2, -0.1, 0.1, -0.2, 0.1, 0.0, -0.1, 0.2, -0.1};
  private static final double RATE_LIMIT = 0.4;
  private static final double AVERAGE_LIMIT = 24;
  private static final double SPREAD_LIMIT = 5;

  Expressions() {
    super("expressions", "Conditions and Expressions", "s", 1_000, List.of(TEMP, AMBIENT),
        List.of(
            Parameter.choice("expression", "Expression", 1, List.of(
                new Option("Rate", "temp.rate().above(0.4)"),
                new Option("Moving average over Samples",
                    "temp.averageOverSamples(${samples}).above(24)"),
                new Option("Moving average over a duration",
                    "temp.averageOverDuration(Duration.ofSeconds(${window})).above(24)"),
                new Option("Arithmetic between Channels", "temp.minus(ambient).above(5)"))),
            new Parameter("samples", "Samples averaged", 5, 1, 20, 1, "Samples"),
            new Parameter("window", "Window", 10, 1, 30, 1, "s")),
        """
        Channel temp = Channel.named("temp");
        Channel ambient = Channel.named("ambient");

        Rule aboveLimit = Rule.named("AboveLimit").when(${expression});
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int i = 0; i < 60; i++) {
      double noise = NOISE[i % NOISE.length];
      double temp = i < 6 ? 26 + noise
          : i == 15 ? 28
          : i < 30 ? 20 + noise
          : i < 38 ? 20 + (i - 30) + noise
          : 28 + noise;
      samples.add(new Sample(i * 1_000L,
          i < 5 ? Map.of(TEMP, temp) : Map.of(TEMP, temp, AMBIENT, 22.0)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Plot plot = plot(values);
    return new Rule[] {Rule.named("AboveLimit").when(plot.expression().above(plot.threshold()))};
  }

  @Override
  Plot plot(Map<String, Double> values) {
    int samples = values.get("samples").intValue();
    double window = values.get("window");
    return switch (values.get("expression").intValue()) {
      case 0 -> new Plot("temp.rate()", TEMP.rate(), RATE_LIMIT);
      case 1 -> new Plot("temp.averageOverSamples(" + samples + ")",
          TEMP.averageOverSamples(samples), AVERAGE_LIMIT);
      case 2 -> new Plot("temp.averageOverDuration(" + (long) window + " s)",
          TEMP.averageOverDuration(Math.round(window * 1_000)), AVERAGE_LIMIT);
      default -> new Plot("temp.minus(ambient)", TEMP.minus(AMBIENT), SPREAD_LIMIT);
    };
  }
}
