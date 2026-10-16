package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.after;
import static io.github.romanhaendler.rbed.Conditions.totalDuration;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** ColdChainExample: a refrigerated truck's temperature and door sensor every minute. */
final class Logistics extends Scenario {
  static final Channel DOOR = Channel.named("door");
  static final Channel TEMP = Channel.named("temp");
  static final long MINUTE = 60_000;

  Logistics() {
    super("logistics", "Logistics", "min", MINUTE, List.of(TEMP, DOOR),
        List.of(
            new Parameter("limit", "Too warm above", 8, 2, 12, 0.5, "°C"),
            new Parameter("excursionMinutes", "Excursion after", 15, 1, 30, 1, "min"),
            new Parameter("doorMinutes", "Caused by the door within", 30, 5, 120, 5, "min"),
            new Parameter("totalMinutes", "Total time limit", 45, 15, 120, 5, "min")),
        """
        import static io.github.romanhaendler.rbed.Conditions.*;

        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.Map;

        Channel door = Channel.named("door");
        Channel temp = Channel.named("temp");

        Rule excursion = Rule.named("Excursion")
            .when(temp.above(${limit})).forDuration(Duration.ofMinutes(${excursionMinutes}))
            .endWhen(temp.above(${limit}).not());
        Rule doorOpen = Rule.named("DoorOpen").when(door.above(0.5));
        Rule excursionAfterDoor = Rule.named("ExcursionAfterDoor")
            .when(after(doorOpen).within(Duration.ofMinutes(${doorMinutes})).and(temp.above(${limit})));
        Rule cumulativeLimit = Rule.named("CumulativeLimit")
            .when(totalDuration(excursion).above(Duration.ofMinutes(${totalMinutes}).toMillis()));
        Detector detector = Detector.builder()
            .rules(excursion, doorOpen, excursionAfterDoor, cumulativeLimit)
            .build();

        for (int m = 0; m < 480; m++) {   // every minute
          double celsius = m >= 62 && m < 90 ? 10 : m >= 300 && m < 330 ? 9 : 5;
          detector.push(m * 60_000L, Map.of(temp, celsius, door, m >= 60 && m < 65 ? 1.0 : 0.0));
        }
        detector.close();
        """);
  }

  static double temperature(int m) {
    if (m >= 62 && m < 90) return 10;
    if (m >= 300 && m < 330) return 9;
    return 5;
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> series = new ArrayList<>();
    for (int m = 0; m < 480; m++) {
      series.add(new Sample(m * MINUTE,
          Map.of(TEMP, temperature(m), DOOR, m >= 60 && m < 65 ? 1.0 : 0.0)));
    }
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    double limit = values.get("limit");
    Rule excursion = Rule.named("Excursion")
        .when(TEMP.above(limit))
        .forDuration(Math.round(values.get("excursionMinutes") * MINUTE))
        .endWhen(TEMP.above(limit).not());
    Rule doorOpen = Rule.named("DoorOpen").when(DOOR.above(0.5));
    Rule excursionAfterDoor = Rule.named("ExcursionAfterDoor")
        .when(after(doorOpen).within(Math.round(values.get("doorMinutes") * MINUTE))
            .and(TEMP.above(limit)));
    Rule cumulativeLimit = Rule.named("CumulativeLimit")
        .when(totalDuration(excursion).above(values.get("totalMinutes") * MINUTE));
    return new Rule[] {excursion, doorOpen, excursionAfterDoor, cumulativeLimit};
  }

  @Override
  List<Plot> plots(Map<String, Double> values) {
    return List.of(new Plot("totalDuration(excursion)",
        totalDuration(rules(values)[0]).dividedBy(MINUTE), values.get("totalMinutes"), "min"));
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(TEMP, "Excursion, ExcursionAfterDoor", "when >", values.get("limit")),
        new Threshold(DOOR, "DoorOpen", "when >", 0.5));
  }

  @Override
  List<Window> windows(Map<String, Double> values) {
    return List.of(new Window("DoorOpen", Math.round(values.get("doorMinutes") * MINUTE)));
  }
}
