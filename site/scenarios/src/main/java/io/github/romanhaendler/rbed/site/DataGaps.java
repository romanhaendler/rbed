package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's Running with {@code maxGap}: a machine's rpm, one Sample per second, running from
 * 6 to 31 s. The sensor is silent for {@link #GAP} ms from a movable moment, so the Samples
 * between are missing.
 */
final class DataGaps extends Scenario {
  static final Channel RPM = Channel.named("rpm");
  static final long GAP = 6_000;

  DataGaps() {
    super("data-gaps", "Data gaps", "s", 1_000, List.of(RPM),
        List.of(
            new Parameter("gap", "Gap from", 12, 0, 33, 1, "s"),
            new Parameter("maxGap", "maxGap", 5, 1, 10, 1, "s")),
        """
        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.Map;

        Channel rpm = Channel.named("rpm");

        Rule running = Rule.named("Running")
            .when(rpm.above(100)).maxGap(Duration.ofSeconds(${maxGap}));
        Detector detector = Detector.builder()
            .rules(running)
            .build();

        for (int s = 0; s < 40; s++) {
          if (s > ${gap} && s < ${gap} + 6) continue;   // the sensor is silent for 6 s
          detector.push(s * 1_000L, Map.of(rpm, s >= 6 && s <= 31 ? 1500.0 : 0.0));
        }
        detector.close();
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    long gapFrom = Math.round(values.get("gap") * 1_000);
    List<Sample> samples = new ArrayList<>();
    for (int s = 0; s < 40; s++) {
      long t = s * 1_000L;
      if (t > gapFrom && t < gapFrom + GAP) continue;
      samples.add(new Sample(t, Map.of(RPM, s >= 6 && s <= 31 ? 1500.0 : 0.0)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    return new Rule[] {
      Rule.named("Running").when(RPM.above(100)).maxGap(Math.round(values.get("maxGap") * 1_000))
    };
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(RPM, "Running", "when >", 100));
  }
}
