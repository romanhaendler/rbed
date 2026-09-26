package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;
import static rbed.Conditions.during;
import static rbed.examples.Series.SECOND;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Detector;
import rbed.Event;
import rbed.Sample;

/**
 * Healthcare: heart rate and oxygen saturation from a bedside monitor at 1 Hz, with a 20 s sensor
 * dropout.
 *
 * <ul>
 *   <li>Tachycardia: heart rate above 100 for 30 s, over below 95 for 30 s. A dropout longer than
 *       10 s interrupts it: the Event Ends truncated, and the one after the dropout starts
 *       truncated because nobody knows what happened in between.
 *   <li>Desaturation: SpO2 below 90 % for 10 s, over at 92 % or more.
 *   <li>HypoxicTachycardia: both at the same time.
 * </ul>
 */
class VitalSignsExample {

  @Test
  void detectsCombinedAlarmsAndHandlesSensorDropout() {
    Detector detector = Detector.builder()
        .rule("Tachycardia", r -> r
            .when(channel("hr").above(100)).forDuration(30 * SECOND)
            .endWhen(channel("hr").below(95)).forDuration(30 * SECOND)
            .maxGap(10 * SECOND))
        .rule("Desaturation", r -> r
            .when(channel("spo2").below(90)).forDuration(10 * SECOND)
            .endWhen(channel("spo2").below(92).not()))
        .rule("HypoxicTachycardia", r -> r
            .when(during("Tachycardia").and(during("Desaturation"))))
        .build();

    List<Sample> series = Series.every(1, 0, 600, SECOND, s -> Map.of(
            "hr", s < 60 ? 80.0 : s < 300 ? 120.0 : 85.0,
            "spo2", s < 120 ? 97.0 : s < 200 ? 86.0 : 96.0))
        .stream()
        .filter(sample -> sample.timestamp() < 150 * SECOND || sample.timestamp() >= 170 * SECOND)
        .toList();
    List<Event> events = detector.detect(series);

    assertEquals(List.of(
            "Tachycardia 60..149 truncatedEnd",   // dropout after 149 s
            "HypoxicTachycardia 130..170",        // Tachycardia only Pending again after dropout
            "Desaturation 120..200",
            "Tachycardia 170..300 truncatedStart"),
        Series.describe(events, SECOND));
  }
}
