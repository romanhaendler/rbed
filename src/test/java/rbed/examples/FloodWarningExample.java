package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;
import static rbed.examples.Samples.HOUR;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Detector;
import rbed.Event;
import rbed.Sample;

/**
 * Environment: a river gauge reporting the water level (cm) every 15 minutes, with one missing
 * report. The level rises 20 cm/h to a crest after 12 h, then falls 10 cm/h.
 *
 * <ul>
 *   <li>FloodWarning: above 300 cm and still rising faster than 10 cm/h for 30 minutes; lifted
 *       only once below 250 cm for 2 hours. Reports more than 2 hours apart interrupt it. The
 *       rate is taken of the average of the last 4 reports, so a noisy gauge would not flicker.
 *   <li>Receding: falling while still above 300 cm.
 * </ul>
 */
class FloodWarningExample {

  static double levelCm(double h) {
    return h <= 12 ? 200 + 20 * h : Math.max(200, 440 - 10 * (h - 12));
  }

  @Test
  void warnsWhileRisingAndLiftsWithHysteresis() {
    Detector detector = Detector.builder()
        .rule("FloodWarning", r -> r
            .when(channel("level").above(300)
                .and(channel("level").averageOverSamples(4).rate().above(10.0 / (HOUR / 1000))))
            .forDuration(HOUR / 2)
            .endWhen(channel("level").below(250)).forDuration(2 * HOUR)
            .maxGap(2 * HOUR))
        .rule("Receding", r -> r
            .when(channel("level").falling().and(channel("level").above(300))))
        .build();

    List<Sample> series = Samples.every(0.25, 0, 72, HOUR, h -> Map.of("level", levelCm(h)))
        .stream().filter(s -> s.timestamp() != 20 * HOUR).toList(); // one missing report
    List<Event> events = detector.detect(series);

    assertEquals(List.of(
            "Receding 12.25..26",       // 437.5 cm at 12.25 h; exactly 300 cm at 26 h
            "FloodWarning 5.25..31.25"), // 305 cm at 5.25 h; 247.5 cm at 31.25 h
        Samples.describe(events, HOUR));
  }
}
