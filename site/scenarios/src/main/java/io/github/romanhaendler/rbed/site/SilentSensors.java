package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Condition;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's silent sensor: a heart rate reported every second, high from 12 s, that falls
 * silent from 25 to 44 s. A timer advances the Detector every second of the silence, so the high
 * reading goes stale and SensorLost is Confirmed without a Sample; without the timer, the
 * Detector notices nothing until the sensor reports again.
 */
final class SilentSensors extends Scenario {
  static final Channel HR = Channel.named("hr");

  SilentSensors() {
    super("silent-sensors", "Silent sensors: advancing time", "s", 1_000, List.of(HR),
        List.of(
            Parameter.choice("timer", "While silent", 0, List.of(
                new Option("A timer advances time", """
                    if (reading != null) detector.push(now, Map.of(hr, reading));
                    else detector.advanceTo(now);  // the timer: time passes without a Sample"""),
                new Option("Nothing happens", """
                    if (reading != null) detector.push(now, Map.of(hr, reading));"""))),
            new Parameter("limit", "Silent after", 5, 1, 15, 1, "s")),
        """
        Channel hr = Channel.named("hr");

        Condition silent = hr.age().above(${limit} * 1_000);
        Rule highHeartRate = Rule.named("HighHeartRate").when(hr.above(120).and(silent.not()));
        Rule sensorLost = Rule.named("SensorLost").when(silent);
        Detector detector = Detector.builder()
            .rules(highHeartRate, sensorLost)
            .build();

        // every second; the sensor is silent from 25 to 44 s, so reading is null then
        ${timer}
        """);
  }

  static boolean silent(long s) {
    return s >= 25 && s < 45;
  }

  /** The Samples that arrive, one a second from 0 to 60 s, none during the silence. */
  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (long s = 0; s <= 60; s++) {
      if (silent(s)) continue;
      double hr = s < 12 ? 78 + s % 3 : s < 25 ? 128 + s % 4 : 92 - s % 3;
      samples.add(new Sample(s * 1_000, Map.of(HR, hr)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Condition silent = HR.age().above(values.get("limit") * 1_000);
    return new Rule[] {
      Rule.named("HighHeartRate").when(HR.above(120).and(silent.not())),
      Rule.named("SensorLost").when(silent)
    };
  }

  /** The age of the heart rate, in seconds, against the limit; rbed's own dividedBy. */
  @Override
  Plot plot(Map<String, Double> values) {
    return new Plot("hr.age()", HR.age().dividedBy(1_000), values.get("limit"), "s");
  }

  /** Pushes the Samples and, if the timer is on, advances time every second of the silence. */
  @Override
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    Detector detector = Detector.builder().rules(rules).listener(lifecycle).build();
    int next = 0;
    for (long s = 0; s <= 60; s++) {
      if (silent(s)) {
        if (values.get("timer") == 0) lifecycle.advance(detector, s * 1_000);
      } else {
        lifecycle.push(detector, samples.get(next++));
      }
    }
    detector.close();
  }
}
