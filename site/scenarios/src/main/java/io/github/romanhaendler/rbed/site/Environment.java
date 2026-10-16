package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FloodWarningExample: a river gauge reporting the water level (cm) every 15 minutes, without
 * the report at 20 h.
 */
final class Environment extends Scenario {
  static final Channel LEVEL = Channel.named("level");
  static final long MINUTE = 60_000;
  static final long HOUR = 60 * MINUTE;

  Environment() {
    super("environment", "Environment", "h", HOUR, List.of(LEVEL),
        List.of(
            new Parameter("warning", "Warning above", 300, 220, 420, 10, "cm"),
            new Parameter("rising", "Rising faster than", 10, 0, 30, 1, "cm/h"),
            new Parameter("lift", "Lifted below", 250, 200, 300, 10, "cm"),
            new Parameter("liftHours", "Lifted after", 2, 0, 6, 1, "h"),
            new Parameter("gapMinutes", "Interrupted by a gap over", 120, 15, 240, 15, "min")),
        """
        Channel level = Channel.named("level");
        long MINUTE = 60_000, HOUR = 60 * MINUTE;

        Rule floodWarning = Rule.named("FloodWarning")
            .when(level.above(${warning})
                .and(level.averageOverSamples(4).rate().above(${rising} / (HOUR / 1000.0))))
            .forDuration(HOUR / 2)
            .endWhen(level.below(${lift})).forDuration(${liftHours} * HOUR)
            .maxGap(${gapMinutes} * MINUTE);
        Rule receding = Rule.named("Receding")
            .when(level.falling().and(level.above(${warning})));
        Detector detector = Detector.builder()
            .rules(floodWarning, receding)
            .build();
        """);
  }

  static double levelCm(double h) {
    return h <= 12 ? 200 + 20 * h : Math.max(200, 440 - 10 * (h - 12));
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> series = new ArrayList<>();
    for (int quarter = 0; quarter < 72 * 4; quarter++) {
      if (quarter == 20 * 4) continue; // one missing report
      double h = quarter / 4.0;
      series.add(new Sample(quarter * 15 * MINUTE, Map.of(LEVEL, levelCm(h))));
    }
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    double warning = values.get("warning");
    Rule floodWarning = Rule.named("FloodWarning")
        .when(LEVEL.above(warning)
            .and(LEVEL.averageOverSamples(4).rate().above(values.get("rising") / (HOUR / 1000.0))))
        .forDuration(HOUR / 2)
        .endWhen(LEVEL.below(values.get("lift")))
        .forDuration(Math.round(values.get("liftHours") * HOUR))
        .maxGap(Math.round(values.get("gapMinutes") * MINUTE));
    Rule receding = Rule.named("Receding")
        .when(LEVEL.falling().and(LEVEL.above(warning)));
    return new Rule[] {floodWarning, receding};
  }
}
