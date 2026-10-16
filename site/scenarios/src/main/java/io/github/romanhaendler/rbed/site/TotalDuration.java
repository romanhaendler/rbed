package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.totalDuration;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's CumulativeLimit: a cold chain's temperature is above 8 °C at minutes 20 to 40, 80
 * to 95 and 150 to 175, 60 minutes in all. CumulativeLimit holds once their summed duration
 * exceeds the limit.
 */
final class TotalDuration extends Scenario {
  static final Channel TEMP = Channel.named("temp");
  static final long MINUTE = 60_000;

  TotalDuration() {
    super("total-duration", "totalDuration", "min", MINUTE, List.of(TEMP),
        List.of(new Parameter("limit", "Limit", 45, 0, 70, 5, "min")),
        """
        Channel temp = Channel.named("temp");

        Rule excursion = Rule.named("Excursion").when(temp.above(8));
        Rule cumulativeLimit = Rule.named("CumulativeLimit")
            .when(totalDuration(excursion).above(${limit} * 60_000));
        Detector detector = Detector.builder()
            .rules(excursion, cumulativeLimit)
            .build();

        detector.push(0,      Map.of(temp, 5.0));   // one Sample a minute
        detector.push(60_000, Map.of(temp, 5.0));
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int m = 0; m <= 240; m++) {
      double temp = m >= 20 && m < 40 || m >= 80 && m < 95 || m >= 150 && m < 175 ? 10 : 5;
      samples.add(new Sample(m * MINUTE, Map.of(TEMP, temp)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule excursion = Rule.named("Excursion").when(TEMP.above(8));
    Rule cumulativeLimit = Rule.named("CumulativeLimit")
        .when(totalDuration(excursion).above(values.get("limit") * MINUTE));
    return new Rule[] {excursion, cumulativeLimit};
  }

  /** totalDuration(excursion), in minutes like the limit. */
  @Override
  Plot plot(Map<String, Double> values) {
    return new Plot("totalDuration(excursion)",
        totalDuration(rules(values)[0]).dividedBy(MINUTE), values.get("limit"), "min");
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(TEMP, "Excursion", "when >", 8));
  }
}
