package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;
import static rbed.examples.Series.MINUTE;
import static rbed.examples.Series.SECOND;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Detector;
import rbed.Event;

/**
 * Automotive: a vehicle's speed (m/s) and engine rpm at 1 Hz.
 *
 * <ul>
 *   <li>Trip: moving faster than 1 m/s for 10 s, over after standing still for 3 minutes. Its
 *       distance is estimated as mean speed x duration.
 *   <li>HarshBraking: decelerating faster than 4 m/s².
 *   <li>Idling: standing with the engine running for 2 minutes.
 * </ul>
 *
 * <p>Gap: the mean in Aggregates weights every Sample equally. With irregular sampling, a
 * time-weighted mean (or an integral, i.e. the distance itself) would be needed.
 */
class FleetTelematicsExample {

  static double speed(double s) {
    if (s < 300) return 0;
    if (s < 310) return 2 * (s - 300);  // accelerating 2 m/s²
    if (s < 900) return 20;
    if (s < 904) return 20 - 5 * (s - 900); // braking 5 m/s²
    return 0;
  }

  @Test
  void detectsTripsHarshBrakingAndIdling() {
    Detector detector = Detector.builder()
        .rule("Trip", r -> r
            .when(channel("speed").above(1)).forDuration(10 * SECOND)
            .endWhen(channel("speed").below(0.5)).forDuration(3 * MINUTE)
            .attribute("km", e -> Math.round(e.aggregates("speed").mean()
                * (e.end().getAsLong() - e.start()) / 1000 / 10) / 100.0))
        .rule("HarshBraking", r -> r.when(channel("speed").rate().below(-4)))
        .rule("Idling", r -> r
            .when(channel("speed").below(0.5).and(channel("rpm").above(600)))
            .forDuration(2 * MINUTE))
        .build();

    List<Event> events = detector.detect(Series.every(1, 0, 1500, SECOND,
        s -> Map.of("speed", speed(s), "rpm", s < 60 ? 0.0 : 800.0)));

    assertEquals(List.of(
            "Idling 60..301",
            "HarshBraking 901..905",
            // Samples 301..903 s: 603 Samples, mean 11940/603 m/s, over 603 s
            "Trip 301..904 {km=11.94}",
            "Idling 904..1499 truncatedEnd"), // last Sample at 1499 s
        Series.describe(events, SECOND));
  }
}
