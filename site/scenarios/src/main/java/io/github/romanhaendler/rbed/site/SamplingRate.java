package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's sampling rate on a recording that is not quite regular: an accelerometer logged at
 * 10 Hz that lost the Samples from 2.1 to 2.9 s. Pushed with their timestamps, both vibrations
 * last long enough; pushed as values with a declared rate, every Sample after the loss is 0.9 s
 * early, and the first vibration is Discarded.
 */
final class SamplingRate extends Scenario {
  static final Channel ACCEL = Channel.named("accel");

  SamplingRate() {
    super("sampling-rate", "Batch processing and sampling rates", "s", 1_000, List.of(ACCEL),
        List.of(
            Parameter.choice("mode", "Push", 0, List.of(
                new Option("With timestamps", """
                    Detector detector = Detector.builder()
                        .rules(vibration)
                        .build();
                    List<Event> events = detector.detect(recording);"""),
                new Option("Values at a sampling rate", """
                    Detector detector = Detector.builder()
                        .samplingRate(${rate})  // Sample i is at round(i * 1000 / ${rate}) ms
                        .rules(vibration)
                        .build();
                    List<Event> events = detector.detectValues(  // the recording's values only
                        recording.stream().map(Sample::values).toList());""")
            )),
            new Parameter("rate", "Declared rate", 10, 1, 20, 1, "Hz")),
        """
        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.ArrayList;
        import java.util.List;
        import java.util.Map;

        Channel accel = Channel.named("accel");
        Rule vibration = Rule.named("Vibration")
            .when(accel.above(3)).forDuration(Duration.ofMillis(1_500));

        // the recording: logged at 10 Hz from 0 to 8 s; the Samples from 2.1 to 2.9 s were lost
        List<Sample> recording = new ArrayList<>();
        for (int tenth = 0; tenth <= 80; tenth++) {
          if (tenth > 20 && tenth < 30) continue;
          boolean shaking = tenth >= 15 && tenth < 35 || tenth >= 50 && tenth < 70;
          recording.add(new Sample(tenth * 100L, Map.of(accel, (shaking ? 4.0 : 0.5) + tenth % 3 * 0.2)));
        }

        ${mode}
        """);
  }

  /** Every 100 ms from 0 to 8 s, except 2.1 to 2.9 s; above 3 from 1.5 to 3.5 s and 5 to 7 s. */
  static List<Sample> recorded() {
    List<Sample> samples = new ArrayList<>();
    for (int tenth = 0; tenth <= 80; tenth++) {
      if (tenth > 20 && tenth < 30) continue;
      boolean shaking = tenth >= 15 && tenth < 35 || tenth >= 50 && tenth < 70;
      // a little ripple, so the line is not flat
      double value = (shaking ? 4.0 : 0.5) + (tenth % 3) * 0.2;
      samples.add(new Sample(tenth * 100L, Map.of(ACCEL, value)));
    }
    return samples;
  }

  /** With a sampling rate, the timestamps rbed derives: round(i * 1000 / rate). */
  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> recorded = recorded();
    if (values.get("mode") == 0) return recorded;
    double hz = values.get("rate");
    List<Sample> derived = new ArrayList<>();
    for (int i = 0; i < recorded.size(); i++) {
      derived.add(new Sample(Math.round(i * 1000 / hz), recorded.get(i).values()));
    }
    return derived;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    return new Rule[] {Rule.named("Vibration").when(ACCEL.above(3)).forDuration(1_500)};
  }

  @Override
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    if (values.get("mode") == 0) {
      super.detect(rules, samples, values, lifecycle);
      return;
    }
    Detector detector = Detector.builder().samplingRate(values.get("rate"))
        .rules(rules).listener(lifecycle).build();
    // samples(values) gave them the derived round(index * 1000 / rate)
    for (Sample sample : samples) lifecycle.pushValues(detector, sample);
    detector.close();
  }
}
