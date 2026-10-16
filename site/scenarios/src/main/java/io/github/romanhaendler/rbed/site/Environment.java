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
        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.Map;

        Channel level = Channel.named("level");

        Rule floodWarning = Rule.named("FloodWarning")
            .when(level.above(${warning})
                .and(level.averageOverSamples(4).rate().above(${rising} / 3600.0)))  // cm/h in cm/s
            .forDuration(Duration.ofMinutes(30))
            .endWhen(level.below(${lift})).forDuration(Duration.ofHours(${liftHours}))
            .maxGap(Duration.ofMinutes(${gapMinutes}));
        Rule receding = Rule.named("Receding")
            .when(level.falling().and(level.above(${warning})));
        Detector detector = Detector.builder()
            .rules(floodWarning, receding)
            .build();

        for (int quarter = 0; quarter < 72 * 4; quarter++) {   // every 15 minutes for 72 h
          if (quarter == 20 * 4) continue;                       // the report at 20 h is missing
          double h = quarter / 4.0;
          double cm = h <= 12 ? 200 + 20 * h : Math.max(200, 440 - 10 * (h - 12));
          detector.push(quarter * 900_000L, Map.of(level, cm));
        }
        detector.close();
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

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(LEVEL, "FloodWarning, Receding", "when >", values.get("warning")),
        new Threshold(LEVEL, "FloodWarning", "endWhen <", values.get("lift")));
  }
}
