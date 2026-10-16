package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's Aggregates: a vehicle with two drives, logged irregularly (every 2 s while the
 * speed changes, every 10 s otherwise), so the plain mean and the time-weighted mean differ.
 * Drive aggregates speed, which its Condition uses, and engine temperature, which it names.
 */
final class AggregatesScenario extends Scenario {
  static final Channel SPEED = Channel.named("speed");
  static final Channel TEMP = Channel.named("temp");

  /** Time (s), speed (m/s), engine temperature (°C). */
  private static final double[][] LOG = {
    {0, 0, 60}, {10, 0, 60}, {20, 0, 60}, {22, 3, 61}, {24, 6, 62}, {26, 9, 63}, {28, 12, 64},
    {30, 14, 65}, {40, 14, 70}, {50, 14, 74}, {60, 14, 77}, {62, 11, 77}, {64, 8, 77},
    {66, 5, 77}, {68, 2, 76}, {70, 0, 76}, {80, 0, 74}, {90, 0, 72}, {100, 0, 70},
    {102, 3, 70}, {104, 6, 71}, {106, 8, 71}, {116, 8, 73}, {126, 8, 75}, {128, 5, 75},
    {130, 2, 75}, {132, 0, 75}, {140, 0, 74}, {150, 0, 73},
  };

  AggregatesScenario() {
    super("aggregates", "Aggregates", "s", 1_000, List.of(SPEED, TEMP),
        List.of(new Parameter("threshold", "Driving above", 1, 0, 13, 1, "m/s")),
        """
        Channel speed = Channel.named("speed");   // m/s
        Channel temp = Channel.named("temp");     // engine temperature, °C

        Rule drive = Rule.named("Drive")
            .when(speed.above(${threshold}))
            .aggregate(temp);                     // speed is aggregated anyway
        Detector detector = Detector.builder()
            .rules(drive)
            .listener(new EventListener() {
              @Override public void onEnded(Event e) {
                Aggregates s = e.aggregates(speed);
                System.out.printf("avg %.1f m/s, max %.1f m/s, %.0f m driven%n",
                    s.timeWeightedMean(), s.max(), s.integral());
              }
            })
            .build();
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (double[] row : LOG) {
      samples.add(new Sample((long) row[0] * 1_000, Map.of(SPEED, row[1], TEMP, row[2])));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    return new Rule[] {
      Rule.named("Drive").when(SPEED.above(values.get("threshold"))).aggregate(TEMP)
    };
  }
}
