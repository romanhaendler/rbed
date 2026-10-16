package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** FleetTelematicsExample: a vehicle's speed (m/s) and engine rpm at 1 Hz. */
final class Automotive extends Scenario {
  static final Channel RPM = Channel.named("rpm");
  static final Channel SPEED = Channel.named("speed");

  Automotive() {
    super("automotive", "Automotive", "s", 1_000, List.of(SPEED, RPM),
        List.of(
            new Parameter("moving", "Moving above", 1, 0.5, 10, 0.5, "m/s"),
            new Parameter("stopMinutes", "Trip over after standing", 3, 1, 10, 1, "min"),
            new Parameter("braking", "Harsh braking above", 4, 1, 8, 0.5, "m/s²"),
            new Parameter("idlingRpm", "Idling above", 600, 300, 1000, 50, "rpm"),
            new Parameter("idlingMinutes", "Idling for", 2, 1, 10, 1, "min")),
        """
        Channel rpm = Channel.named("rpm");
        Channel speed = Channel.named("speed");
        long SECOND = 1_000, MINUTE = 60 * SECOND;

        Rule trip = Rule.named("Trip")
            .when(speed.above(${moving})).forDuration(10 * SECOND)
            .endWhen(speed.below(0.5)).forDuration(${stopMinutes} * MINUTE);
        Rule harshBraking = Rule.named("HarshBraking").when(speed.rate().below(-${braking}));
        Rule idling = Rule.named("Idling")
            .when(speed.below(0.5).and(rpm.above(${idlingRpm})))
            .forDuration(${idlingMinutes} * MINUTE);
        Detector detector = Detector.builder()
            .rules(trip, harshBraking, idling)
            .build();

        // Distance of a Trip in km: the integral of speed (m/s) over time
        double km(Event trip) {
          return trip.aggregates(speed).integral() / 1000;
        }
        """);
  }

  static double speed(double s) {
    if (s < 300) return 0;
    if (s < 310) return 2 * (s - 300);  // accelerating 2 m/s²
    if (s < 900) return 20;
    if (s < 904) return 20 - 5 * (s - 900); // braking 5 m/s²
    return 0;
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> series = new ArrayList<>();
    for (int s = 0; s < 1500; s++) {
      series.add(new Sample(s * 1_000L, Map.of(SPEED, speed(s), RPM, s < 60 ? 0.0 : 800.0)));
    }
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule trip = Rule.named("Trip")
        .when(SPEED.above(values.get("moving"))).forDuration(10_000)
        .endWhen(SPEED.below(0.5)).forDuration(Math.round(values.get("stopMinutes") * 60_000));
    Rule harshBraking = Rule.named("HarshBraking")
        .when(SPEED.rate().below(-values.get("braking")));
    Rule idling = Rule.named("Idling")
        .when(SPEED.below(0.5).and(RPM.above(values.get("idlingRpm"))))
        .forDuration(Math.round(values.get("idlingMinutes") * 60_000));
    return new Rule[] {trip, harshBraking, idling};
  }

  /** A Trip's distance in km, to two decimals (built by hand, as TeaVM formats doubles its own way). */
  @Override
  String note(Event event, Map<String, Double> values) {
    if (!event.name().equals("Trip")) return null;
    long hundredths = Math.round(event.aggregates(SPEED).integral() / 10);
    long cents = hundredths % 100;
    return hundredths / 100 + (cents < 10 ? ".0" : ".") + cents + " km";
  }
}
