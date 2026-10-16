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
            Parameter.toggle("samplingRate", "Sampling rate mode", false),
            new Parameter("rate", "Declared rate", 10, 1, 20, 1, "Hz")),
        """
        Channel accel = Channel.named("accel");
        Rule vibration = Rule.named("Vibration").when(accel.above(3)).forDuration(1_500);

        boolean samplingRate = ${samplingRate};
        Detector.Builder builder = Detector.builder().rules(vibration);
        if (samplingRate) builder.samplingRate(${rate});  // Sample i is at round(i * 1000 / ${rate})
        Detector detector = builder.build();

        // logged at 10 Hz, 0 to 8 s; the Samples from 2.1 to 2.9 s were lost
        for (Sample s : recording) {
          if (samplingRate) detector.push(s.values());
          else detector.push(s.timestamp(), s.values());
        }
        detector.close();
        """);
  }

  /** Every 100 ms from 0 to 8 s, except 2.1 to 2.9 s; above 3 from 1.5 to 3.5 s and 5 to 7 s. */
  @Override
  List<Sample> samples() {
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
    if (!on(values, "samplingRate")) return samples();
    double hz = values.get("rate");
    List<Sample> recorded = samples();
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
  void detect(Rule[] rules, Map<String, Double> values, Lifecycle lifecycle) {
    if (!on(values, "samplingRate")) {
      detect(rules, lifecycle);
      return;
    }
    Detector detector = Detector.builder().samplingRate(values.get("rate"))
        .rules(rules).listener(lifecycle).build();
    for (Sample sample : samples(values)) {
      lifecycle.now = sample.timestamp();  // the derived round(index * 1000 / rate)
      detector.push(sample.values());
    }
    detector.close();
  }
}
