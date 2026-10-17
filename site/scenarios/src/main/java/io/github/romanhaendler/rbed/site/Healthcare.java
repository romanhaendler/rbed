package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.during;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * VitalSignsExample: heart rate and SpO2 at 1 Hz with a 20 s sensor dropout, during which the
 * monitor's clock advances the Detector every second.
 */
final class Healthcare extends Scenario {
  static final Channel HR = Channel.named("hr");
  static final Channel SPO2 = Channel.named("spo2");

  Healthcare() {
    super("healthcare", "Healthcare", "s", 1_000, List.of(HR, SPO2),
        List.of(
            new Parameter("tachycardia", "Tachycardia above", 100, 85, 115, 5, "bpm"),
            new Parameter("tachycardiaSeconds", "Tachycardia for", 30, 0, 60, 5, "s"),
            new Parameter("maxGap", "Max gap", 10, 2, 30, 1, "s"),
            new Parameter("desaturation", "Desaturation below", 90, 85, 95, 1, "%"),
            new Parameter("lost", "Sensor lost after", 5, 1, 20, 1, "s")),
        """
        import static io.github.romanhaendler.rbed.Conditions.*;

        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.Map;

        Channel hr = Channel.named("hr");
        Channel spo2 = Channel.named("spo2");

        Rule tachycardia = Rule.named("Tachycardia")
            .when(hr.above(${tachycardia})).forDuration(Duration.ofSeconds(${tachycardiaSeconds}))
            .endWhen(hr.below(95)).forDuration(Duration.ofSeconds(30))
            .maxGap(Duration.ofSeconds(${maxGap}));
        Rule desaturation = Rule.named("Desaturation")
            .when(spo2.below(${desaturation})).forDuration(Duration.ofSeconds(10))
            .endWhen(spo2.below(92).not());
        Rule hypoxicTachycardia = Rule.named("HypoxicTachycardia")
            .when(during(tachycardia).and(during(desaturation)));
        Rule sensorLost = Rule.named("SensorLost")
            .when(hr.age().above(Duration.ofSeconds(${lost}).toMillis()));
        Detector detector = Detector.builder()
            .rules(tachycardia, desaturation, hypoxicTachycardia, sensorLost)
            .build();

        for (long s = 0; s < 600; s++) {   // every second
          if (s >= 150 && s < 170) {
            detector.advanceTo(s * 1_000);   // the sensor drops out; the monitor's clock advances time
          } else {
            detector.push(s * 1_000, Map.of(
                hr, s < 60 ? 80.0 : s < 300 ? 120.0 : 85.0,
                spo2, s < 120 ? 97.0 : s < 200 ? 86.0 : 96.0));
          }
        }
        detector.close();
        """);
  }

  static boolean dropout(long s) {
    return s >= 150 && s < 170;
  }

  static Map<Channel, Double> values(long s) {
    return Map.of(
        HR, s < 60 ? 80.0 : s < 300 ? 120.0 : 85.0,
        SPO2, s < 120 ? 97.0 : s < 200 ? 86.0 : 96.0);
  }

  /** The Samples that arrive: none during the dropout. */
  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> series = new ArrayList<>();
    for (long s = 0; s < 600; s++) {
      if (!dropout(s)) series.add(new Sample(s * 1_000, values(s)));
    }
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule tachycardia = Rule.named("Tachycardia")
        .when(HR.above(values.get("tachycardia")))
        .forDuration(Math.round(values.get("tachycardiaSeconds") * 1000))
        .endWhen(HR.below(95)).forDuration(30_000)
        .maxGap(Math.round(values.get("maxGap") * 1000));
    Rule desaturation = Rule.named("Desaturation")
        .when(SPO2.below(values.get("desaturation"))).forDuration(10_000)
        .endWhen(SPO2.below(92).not());
    Rule hypoxicTachycardia = Rule.named("HypoxicTachycardia")
        .when(during(tachycardia).and(during(desaturation)));
    Rule sensorLost = Rule.named("SensorLost")
        .when(HR.age().above(values.get("lost") * 1000));
    return new Rule[] {tachycardia, desaturation, hypoxicTachycardia, sensorLost};
  }

  /** Pushes a Sample every second, and advances time instead during the dropout. */
  @Override
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    Detector detector = lifecycle.builder().rules(rules).build();
    int next = 0;
    for (long s = 0; s < 600; s++) {
      if (dropout(s)) lifecycle.advance(detector, s * 1_000);
      else lifecycle.push(detector, samples.get(next++));
    }
    detector.close();
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(HR, "Tachycardia", "when >", values.get("tachycardia")),
        new Threshold(HR, "Tachycardia", "endWhen <", 95),
        new Threshold(SPO2, "Desaturation", "when <", values.get("desaturation")),
        new Threshold(SPO2, "Desaturation", "endWhen ≥", 92));
  }
}
