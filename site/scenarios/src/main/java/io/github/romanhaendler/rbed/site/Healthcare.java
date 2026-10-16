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
        Channel hr = Channel.named("hr");
        Channel spo2 = Channel.named("spo2");
        long SECOND = 1_000;

        Rule tachycardia = Rule.named("Tachycardia")
            .when(hr.above(${tachycardia})).forDuration(${tachycardiaSeconds} * SECOND)
            .endWhen(hr.below(95)).forDuration(30 * SECOND)
            .maxGap(${maxGap} * SECOND);
        Rule desaturation = Rule.named("Desaturation")
            .when(spo2.below(${desaturation})).forDuration(10 * SECOND)
            .endWhen(spo2.below(92).not());
        Rule hypoxicTachycardia = Rule.named("HypoxicTachycardia")
            .when(during(tachycardia).and(during(desaturation)));
        Rule sensorLost = Rule.named("SensorLost").when(hr.age().above(${lost} * SECOND));
        Detector detector = Detector.builder()
            .rules(tachycardia, desaturation, hypoxicTachycardia, sensorLost)
            .listener(listener)
            .build();

        // every second: a Sample, or, during the dropout from 150 to 170 s, a time advance
        if (dropout) detector.advanceTo(timestamp);
        else detector.push(timestamp, values);
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
  List<Sample> samples() {
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
  void detect(Rule[] rules, List<Sample> samples, Lifecycle lifecycle) {
    Detector detector = Detector.builder().rules(rules).listener(lifecycle).build();
    for (long s = 0; s < 600; s++) {
      lifecycle.now = s * 1_000;
      if (dropout(s)) detector.advanceTo(s * 1_000);
      else detector.push(s * 1_000, values(s));
    }
    detector.close();
  }
}
