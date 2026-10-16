package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.during;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's HypoxicTachycardia: a heart rate above 100 and an SpO2 below 90, each with its own
 * duration, combined with {@code during}. Heart rate is high from 40 to 200 s and briefly from 230
 * to 250 s; SpO2 dips for 5 s at 20 s, from 100 to 160 s and from 230 to 260 s.
 */
final class During extends Scenario {
  static final Channel HR = Channel.named("hr");
  static final Channel SPO2 = Channel.named("spo2");

  During() {
    super("during", "during", "s", 1_000, List.of(HR, SPO2),
        List.of(
            new Parameter("tachycardia", "Tachycardia duration", 30, 0, 60, 5, "s"),
            new Parameter("desaturation", "Desaturation duration", 10, 0, 30, 5, "s")),
        """
        Channel hr = Channel.named("hr");
        Channel spo2 = Channel.named("spo2");

        Rule tachycardia = Rule.named("Tachycardia").when(hr.above(100)).forDuration(${tachycardia} * 1_000);
        Rule desaturation = Rule.named("Desaturation").when(spo2.below(90)).forDuration(${desaturation} * 1_000);
        Rule hypoxicTachycardia = Rule.named("HypoxicTachycardia")
            .when(during(tachycardia).and(during(desaturation)));
        Detector detector = Detector.builder()
            .rules(tachycardia, desaturation, hypoxicTachycardia)
            .build();

        detector.push(0,     Map.of(hr, 75.0, spo2, 97.0));   // one Sample every 5 s
        detector.push(5_000, Map.of(hr, 75.0, spo2, 97.0));
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int s = 0; s <= 300; s += 5) {
      double hr = s >= 40 && s < 200 ? 115 : s >= 230 && s < 250 ? 110 : 75;
      double spo2 = s == 20 ? 86 : s >= 100 && s < 160 ? 87 : s >= 230 && s < 260 ? 86 : 97;
      samples.add(new Sample(s * 1_000L, Map.of(HR, hr, SPO2, spo2)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule tachycardia = Rule.named("Tachycardia").when(HR.above(100))
        .forDuration(Math.round(values.get("tachycardia") * 1_000));
    Rule desaturation = Rule.named("Desaturation").when(SPO2.below(90))
        .forDuration(Math.round(values.get("desaturation") * 1_000));
    Rule hypoxicTachycardia = Rule.named("HypoxicTachycardia")
        .when(during(tachycardia).and(during(desaturation)));
    return new Rule[] {tachycardia, desaturation, hypoxicTachycardia};
  }
}
