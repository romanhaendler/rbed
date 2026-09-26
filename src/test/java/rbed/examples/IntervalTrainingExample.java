package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.after;
import static rbed.Conditions.channel;
import static rbed.Conditions.count;
import static rbed.examples.Series.MINUTE;
import static rbed.examples.Series.SECOND;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Detector;
import rbed.Event;
import rbed.EventListener;

/**
 * Sports: a runner's speed (m/s) and heart rate at 1 Hz during an interval session. After a
 * one-minute warm-up: three reps of 30 s sprint at 7 m/s and 90 s jog. Heart rate climbs by 1 bpm/s
 * from 140 while sprinting and drops by 1 bpm/s while jogging.
 *
 * <ul>
 *   <li>Sprint: faster than 6 m/s for 5 s, over below 4 m/s.
 *   <li>Recovered: heart rate below 120 within 2 minutes after a Sprint.
 *   <li>SessionDone: the third Sprint was Confirmed.
 * </ul>
 *
 * <p>The recovery time (from the end of a Sprint to Recovered) is computed by a listener.
 */
class IntervalTrainingExample {

  static double heartRate(double s) {
    if (s < 60) return 100;
    double inRep = (s - 60) % 120;
    return inRep < 30 ? 140 + inRep : 170 - (inRep - 30);
  }

  @Test
  void detectsRepsAndRecoveryTimes() {
    List<Long> recoverySeconds = new ArrayList<>();
    Detector detector = Detector.builder()
        .rule("Sprint", r -> r
            .when(channel("speed").above(6)).forDuration(5 * SECOND)
            .endWhen(channel("speed").below(4)))
        .rule("Recovered", r -> r
            .when(after("Sprint").within(2 * MINUTE).and(channel("hr").below(120))))
        .rule("SessionDone", r -> r.when(count("Sprint").above(2)))
        .listener(new EventListener() {
          private long lastSprintEnd;

          @Override public void onEnded(Event e) {
            if (e.name().equals("Sprint")) lastSprintEnd = e.end().getAsLong();
          }

          @Override public void onConfirmed(Event e) {
            if (e.name().equals("Recovered")) {
              recoverySeconds.add((e.start() - lastSprintEnd) / SECOND);
            }
          }
        })
        .build();

    List<Event> events = detector.detect(Series.every(1, 0, 420, SECOND, s -> Map.of(
        "speed", s >= 60 && (s - 60) % 120 < 30 ? 7.0 : 2.0,
        "hr", heartRate(s))));

    assertEquals(List.of(
            "Sprint 60..90", "Recovered 141..180",
            "Sprint 180..210", "Recovered 261..300",
            "Sprint 300..330", "Recovered 381..419 truncatedEnd",
            "SessionDone 305..419 truncatedEnd"), // third Sprint Confirmed at 305 s
        Series.describe(events, SECOND));
    assertEquals(List.of(51L, 51L, 51L), recoverySeconds);
  }
}
