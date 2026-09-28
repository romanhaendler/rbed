package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.examples.Samples.MINUTE;
import static rbed.examples.Samples.SECOND;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Channel;
import rbed.Detector;
import rbed.Event;

/**
 * Automotive: a vehicle's speed (m/s) and engine rpm at 1 Hz.
 *
 * <ul>
 *   <li>Trip: moving faster than 1 m/s for 10 s, over after standing still for 3 minutes. Its
 *       distance is the integral of speed over time, see {@link #km}.
 *   <li>HarshBraking: decelerating faster than 4 m/s².
 *   <li>Idling: standing with the engine running for 2 minutes.
 * </ul>
 */
class FleetTelematicsExample {
  static final Channel RPM = Channel.named("rpm");
  static final Channel SPEED = Channel.named("speed");

  static double speed(double s) {
    if (s < 300) return 0;
    if (s < 310) return 2 * (s - 300);  // accelerating 2 m/s²
    if (s < 900) return 20;
    if (s < 904) return 20 - 5 * (s - 900); // braking 5 m/s²
    return 0;
  }

  /** Distance of a Trip in km: the integral of speed (m/s) over time. */
  static double km(Event trip) {
    return trip.aggregates(SPEED).integral() / 1000;
  }

  @Test
  void detectsTripsHarshBrakingAndIdling() {
    Detector detector = Detector.builder()
        .rule("Trip", r -> r
            .when(SPEED.above(1)).forDuration(10 * SECOND)
            .endWhen(SPEED.below(0.5)).forDuration(3 * MINUTE))
        .rule("HarshBraking", r -> r.when(SPEED.rate().below(-4)))
        .rule("Idling", r -> r
            .when(SPEED.below(0.5).and(RPM.above(600)))
            .forDuration(2 * MINUTE))
        .build();

    List<Event> events = detector.detect(Samples.every(1, 0, 1500, SECOND,
        s -> Map.of(SPEED, speed(s), RPM, s < 60 ? 0.0 : 800.0)));

    assertEquals(List.of(
            "Idling 60..301",
            "HarshBraking 901..905",
            "Trip 301..904",
            "Idling 904..1499 truncatedEnd"), // last Sample at 1499 s
        Samples.describe(events, SECOND));
    // each speed from 301 to 903 s holds for 1 s: 11940 m
    assertEquals(11.94, km(events.get(2)), 1e-9);
  }
}
