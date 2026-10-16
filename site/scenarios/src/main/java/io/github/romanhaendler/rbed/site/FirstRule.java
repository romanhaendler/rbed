package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.List;
import java.util.Map;

/** The README's first Rule: HighTemperature while temp is above a threshold. */
final class FirstRule extends Scenario {
  static final Channel TEMP = Channel.named("temp");

  FirstRule() {
    super("first-rule", "A first Rule", "s", 1_000, List.of(TEMP),
        List.of(new Parameter("threshold", "Threshold", 80, 60, 90, 1, "°C")),
        """
        Channel temp = Channel.named("temp");

        Rule highTemperature = Rule.named("HighTemperature").when(temp.above(${threshold}));
        Detector detector = Detector.builder()
            .rules(highTemperature)
            .listener(new EventListener() {
              @Override public void onEnded(Event e) {
                System.out.println(e.name() + " from " + e.start() + " to " + e.end().getAsLong());
              }
            })
            .build();

        detector.push(1_000, Map.of(temp, 75.0));   // timestamp in ms, then Channel values
        detector.push(2_000, Map.of(temp, 85.0));
        detector.push(3_000, Map.of(temp, 70.0));
        detector.close();
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    return List.of(
        new Sample(1_000, Map.of(TEMP, 75.0)),
        new Sample(2_000, Map.of(TEMP, 85.0)),
        new Sample(3_000, Map.of(TEMP, 70.0)));
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    return new Rule[] {Rule.named("HighTemperature").when(TEMP.above(values.get("threshold")))};
  }
}
