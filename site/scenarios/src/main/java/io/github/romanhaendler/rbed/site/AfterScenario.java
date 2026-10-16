package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.after;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's ExcursionAfterDoor: a cold room's door opens at minutes 10 to 12 and at minute
 * 60; its temperature is above 8 °C at minutes 15 to 25, soon after the first opening, and at
 * minutes 100 to 110, long after the second. The window decides which excursion counts.
 */
final class AfterScenario extends Scenario {
  static final Channel DOOR = Channel.named("door");
  static final Channel TEMP = Channel.named("temp");
  static final long MINUTE = 60_000;

  AfterScenario() {
    super("after", "after", "min", MINUTE, List.of(TEMP, DOOR),
        List.of(new Parameter("window", "Window after DoorOpen", 30, 0, 60, 5, "min")),
        """
        import static io.github.romanhaendler.rbed.Conditions.*;

        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.Map;

        Channel door = Channel.named("door");
        Channel temp = Channel.named("temp");

        Rule doorOpen = Rule.named("DoorOpen").when(door.above(0.5));
        Rule excursionAfterDoor = Rule.named("ExcursionAfterDoor")
            .when(after(doorOpen).within(Duration.ofMinutes(${window})).and(temp.above(8)));
        Detector detector = Detector.builder()
            .rules(doorOpen, excursionAfterDoor)
            .build();

        for (int m = 0; m <= 150; m++) {   // one Sample a minute
          detector.push(m * 60_000L, Map.of(
              door, m >= 10 && m < 12 || m == 60 ? 1.0 : 0.0,
              temp, m >= 15 && m < 25 || m >= 100 && m < 110 ? 10.0 : 4.0));
        }
        detector.close();
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int m = 0; m <= 150; m++) {
      double door = m >= 10 && m < 12 || m == 60 ? 1 : 0;
      double temp = m >= 15 && m < 25 || m >= 100 && m < 110 ? 10 : 4;
      samples.add(new Sample(m * MINUTE, Map.of(DOOR, door, TEMP, temp)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule doorOpen = Rule.named("DoorOpen").when(DOOR.above(0.5));
    Rule excursionAfterDoor = Rule.named("ExcursionAfterDoor")
        .when(after(doorOpen).within(Math.round(values.get("window") * MINUTE))
            .and(TEMP.above(8)));
    return new Rule[] {doorOpen, excursionAfterDoor};
  }
}
