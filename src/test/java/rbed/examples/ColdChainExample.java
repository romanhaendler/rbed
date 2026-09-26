package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.after;
import static rbed.Conditions.channel;
import static rbed.examples.Series.MINUTE;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Detector;
import rbed.Event;
import rbed.EventListener;

/**
 * Logistics: a refrigerated truck carrying vaccines (2-8 °C), temperature and door sensor every
 * minute.
 *
 * <ul>
 *   <li>Excursion: above 8 °C for 15 minutes, over as soon as it is back at 8 °C or below.
 *   <li>DoorOpen: the door sensor reads 1.
 *   <li>ExcursionAfterDoor: above 8 °C within 30 minutes after the door closed, to attribute the
 *       cause.
 * </ul>
 *
 * <p>Gap: regulations limit the <i>cumulative</i> time above 8 °C over the whole trip. That spans
 * several Events and cannot be a Rule; here a listener adds up the Excursions.
 */
class ColdChainExample {

  static double temperature(double m) {
    if (m >= 62 && m < 90) return 10;
    if (m >= 300 && m < 330) return 9;
    return 5;
  }

  @Test
  void detectsExcursionsAndTheirCause() {
    long[] minutesAbove8 = {0};
    Detector detector = Detector.builder()
        .rule("Excursion", r -> r
            .when(channel("temp").above(8)).forDuration(15 * MINUTE)
            .endWhen(channel("temp").above(8).not()))
        .rule("DoorOpen", r -> r.when(channel("door").above(0.5)))
        .rule("ExcursionAfterDoor", r -> r
            .when(after("DoorOpen").within(30 * MINUTE).and(channel("temp").above(8))))
        .listener(new EventListener() {
          @Override public void onEnded(Event e) {
            if (e.name().equals("Excursion")) {
              minutesAbove8[0] += (e.end().getAsLong() - e.start()) / MINUTE;
            }
          }
        })
        .build();

    List<Event> events = detector.detect(Series.every(1, 0, 480, MINUTE, m -> Map.of(
        "temp", temperature(m),
        "door", m >= 60 && m < 65 ? 1.0 : 0.0)));

    assertEquals(List.of(
            "DoorOpen 60..65",
            "Excursion 62..90",
            "ExcursionAfterDoor 65..90", // from the moment the door closed
            "Excursion 300..330"),       // unrelated to the door: too long after it closed
        Series.describe(events, MINUTE));
    assertEquals(28 + 30, minutesAbove8[0]);
  }
}
