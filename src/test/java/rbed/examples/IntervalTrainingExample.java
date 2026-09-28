package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.after;
import static rbed.Conditions.count;
import static rbed.examples.Samples.MINUTE;
import static rbed.examples.Samples.SECOND;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Channel;
import rbed.Detector;
import rbed.Event;
import rbed.Rule;
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
  static final Channel HR = Channel.named("hr");
  static final Channel SPEED = Channel.named("speed");

  static double heartRate(double s) {
    if (s < 60) return 100;
    double inRep = (s - 60) % 120;
    return inRep < 30 ? 140 + inRep : 170 - (inRep - 30);
  }

  @Test
  void detectsRepsAndRecoveryTimes() {
    List<Long> recoverySeconds = new ArrayList<>();
    Rule sprint = Rule.named("Sprint")
        .when(SPEED.above(6)).forDuration(5 * SECOND)
        .endWhen(SPEED.below(4));
    Rule recovered = Rule.named("Recovered")
        .when(after(sprint).within(2 * MINUTE).and(HR.below(120)));
    Rule sessionDone = Rule.named("SessionDone").when(count(sprint).above(2));
    Detector detector = Detector.builder()
        .rules(sprint, recovered, sessionDone)
        .listener(new EventListener() {
          private long lastSprintEnd;

          @Override public void onEnded(Event e) {
            if (e.is(sprint)) lastSprintEnd = e.end().getAsLong();
          }

          @Override public void onConfirmed(Event e) {
            if (e.is(recovered)) {
              recoverySeconds.add((e.start() - lastSprintEnd) / SECOND);
            }
          }
        })
        .build();

    List<Event> events = detector.detect(Samples.every(1, 0, 420, SECOND, s -> Map.of(
        SPEED, s >= 60 && (s - 60) % 120 < 30 ? 7.0 : 2.0,
        HR, heartRate(s))));

    assertEquals(List.of(
            "Sprint 60..90", "Recovered 141..180",
            "Sprint 180..210", "Recovered 261..300",
            "Sprint 300..330", "Recovered 381..419 truncatedEnd",
            "SessionDone 305..419 truncatedEnd"), // third Sprint Confirmed at 305 s
        Samples.describe(events, SECOND));
    assertEquals(List.of(51L, 51L, 51L), recoverySeconds);
  }
}
