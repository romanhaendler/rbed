package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.after;
import static rbed.Conditions.totalDuration;
import static rbed.examples.Samples.MINUTE;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Channel;
import rbed.Detector;
import rbed.Event;
import rbed.Rule;

/**
 * Logistics: a refrigerated truck carrying vaccines (2-8 °C), temperature and door sensor every
 * minute.
 *
 * <ul>
 *   <li>Excursion: above 8 °C for 15 minutes, over as soon as it is back at 8 °C or below.
 *   <li>DoorOpen: the door sensor reads 1.
 *   <li>ExcursionAfterDoor: above 8 °C within 30 minutes after the door closed, to attribute the
 *       cause.
 *   <li>CumulativeLimit: regulations limit the total time above 8 °C over the trip to 45
 *       minutes, across all Excursions.
 * </ul>
 */
class ColdChainExample {
  static final Channel DOOR = Channel.named("door");
  static final Channel TEMP = Channel.named("temp");

  static double temperature(double m) {
    if (m >= 62 && m < 90) return 10;
    if (m >= 300 && m < 330) return 9;
    return 5;
  }

  @Test
  void detectsExcursionsAndTheirCause() {
    Rule excursion = Rule.named("Excursion")
        .when(TEMP.above(8)).forDuration(15 * MINUTE)
        .endWhen(TEMP.above(8).not());
    Rule doorOpen = Rule.named("DoorOpen").when(DOOR.above(0.5));
    Rule excursionAfterDoor = Rule.named("ExcursionAfterDoor")
        .when(after(doorOpen).within(30 * MINUTE).and(TEMP.above(8)));
    Rule cumulativeLimit = Rule.named("CumulativeLimit")
        .when(totalDuration(excursion).above(45 * MINUTE));
    Detector detector = Detector.builder()
        .rules(excursion, doorOpen, excursionAfterDoor, cumulativeLimit)
        .build();

    List<Event> events = detector.detect(Samples.every(1, 0, 480, MINUTE, m -> Map.of(
        TEMP, temperature(m),
        DOOR, m >= 60 && m < 65 ? 1.0 : 0.0)));

    assertEquals(List.of(
            "DoorOpen 60..65",
            "Excursion 62..90",
            "ExcursionAfterDoor 65..90", // from the moment the door closed
            "Excursion 300..330",        // unrelated to the door: too long after it closed
            // 28 minutes from the first; the second, Confirmed at 315, adds up to 46 at 318
            "CumulativeLimit 318..479 truncatedEnd"),
        Samples.describe(events, MINUTE));
  }
}
