package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The README's Samples and Channels: two sensors at different rates share one Detector. The
 * temperature reports every second and rises from 21 °C to 30 °C; the humidity reports every few
 * seconds and rises from 50 % to 65 % at 30 s, then falls again. Between its reports, the humidity
 * keeps its last value, so how often it reports decides where Muggy starts and Ends.
 */
final class SamplesAndChannels extends Scenario {
  static final Channel TEMP = Channel.named("temp");
  static final Channel HUMIDITY = Channel.named("humidity");

  SamplesAndChannels() {
    super("samples-and-channels", "Samples and Channels", "s", 1_000, List.of(TEMP, HUMIDITY),
        List.of(new Parameter("every", "Humidity reported every", 10, 1, 15, 1, "s")),
        """
        import io.github.romanhaendler.rbed.*;
        import java.util.HashMap;
        import java.util.Map;

        Channel temp = Channel.named("temp");
        Channel humidity = Channel.named("humidity");

        Rule muggy = Rule.named("Muggy").when(temp.above(26).and(humidity.above(60)));
        Detector detector = Detector.builder()
            .rules(muggy)
            .build();

        for (int s = 0; s <= 60; s++) {
          Map<Channel, Double> values = new HashMap<>();
          values.put(temp, Math.min(30, 21 + 0.5 * s));   // temp: every second
          if (s % ${every} == 0) {   // humidity: every ${every} s
            values.put(humidity, s <= 30 ? 50 + 0.5 * s : 65 - 0.5 * (s - 30));
          }
          detector.push(s * 1_000L, values);   // a missing Channel keeps its last value
        }
        detector.close();
        """);
  }

  static double temperature(int s) {
    return Math.min(30, 21 + 0.5 * s);
  }

  static double humidity(int s) {
    return s <= 30 ? 50 + 0.5 * s : 65 - 0.5 * (s - 30);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    long every = Math.round(values.get("every"));
    List<Sample> samples = new ArrayList<>();
    for (int s = 0; s <= 60; s++) {
      Map<Channel, Double> sample = new HashMap<>();
      sample.put(TEMP, temperature(s));
      if (s % every == 0) sample.put(HUMIDITY, humidity(s));
      samples.add(new Sample(s * 1_000L, sample));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    return new Rule[] {Rule.named("Muggy").when(TEMP.above(26).and(HUMIDITY.above(60)))};
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(TEMP, "Muggy", "when >", 26),
        new Threshold(HUMIDITY, "Muggy", "when >", 60));
  }
}
