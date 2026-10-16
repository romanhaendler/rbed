package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.after;
import static io.github.romanhaendler.rbed.Conditions.count;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.EventListener;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * IntervalTrainingExample: a runner's speed (m/s) and heart rate at 1 Hz, a warm-up minute and
 * three reps of 30 s sprint and 90 s jog. The listener times each recovery.
 */
final class Sports extends Scenario {
  static final Channel HR = Channel.named("hr");
  static final Channel SPEED = Channel.named("speed");
  static final long SECOND = 1_000;

  Sports() {
    super("sports", "Sports", "s", SECOND, List.of(SPEED, HR),
        List.of(
            new Parameter("sprint", "Sprint above", 6, 3, 8, 0.5, "m/s"),
            new Parameter("sprintSeconds", "Sprint for", 5, 1, 30, 1, "s"),
            new Parameter("recoveredHr", "Recovered below", 120, 100, 160, 5, "bpm"),
            new Parameter("recoveryMinutes", "Recovered within", 2, 1, 5, 1, "min"),
            new Parameter("sprints", "Done after more than", 2, 0, 3, 1, "Sprints")),
        """
        import static io.github.romanhaendler.rbed.Conditions.*;

        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.ArrayList;
        import java.util.List;
        import java.util.Map;

        Channel hr = Channel.named("hr");
        Channel speed = Channel.named("speed");

        Rule sprint = Rule.named("Sprint")
            .when(speed.above(${sprint})).forDuration(Duration.ofSeconds(${sprintSeconds}))
            .endWhen(speed.below(4));
        Rule recovered = Rule.named("Recovered")
            .when(after(sprint).within(Duration.ofMinutes(${recoveryMinutes})).and(hr.below(${recoveredHr})));
        Rule sessionDone = Rule.named("SessionDone").when(count(sprint).above(${sprints}));
        List<Long> recoverySeconds = new ArrayList<>();
        Detector detector = Detector.builder()
            .rules(sprint, recovered, sessionDone)
            .listener(new EventListener() {
              private long lastSprintEnd;

              @Override public void onEnded(Event e) {
                if (e.is(sprint)) lastSprintEnd = e.end().getAsLong();
              }

              // The recovery time, from the end of a Sprint to Recovered
              @Override public void onConfirmed(Event e) {
                if (e.is(recovered)) recoverySeconds.add((e.start() - lastSprintEnd) / 1_000);
              }
            })
            .build();

        for (int s = 0; s < 420; s++) {   // at 1 Hz: a warm-up minute, then reps of 30 s sprint, 90 s jog
          int inRep = (s - 60) % 120;
          boolean sprinting = s >= 60 && inRep < 30;
          double heartRate = s < 60 ? 100 : inRep < 30 ? 140 + inRep : 170 - (inRep - 30);
          detector.push(s * 1_000L, Map.of(speed, sprinting ? 7.0 : 2.0, hr, heartRate));
        }
        detector.close();
        """);
  }

  static double heartRate(int s) {
    if (s < 60) return 100;
    int inRep = (s - 60) % 120;
    return inRep < 30 ? 140 + inRep : 170 - (inRep - 30);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> series = new ArrayList<>();
    for (int s = 0; s < 420; s++) {
      series.add(new Sample(s * SECOND, Map.of(
          SPEED, s >= 60 && (s - 60) % 120 < 30 ? 7.0 : 2.0,
          HR, heartRate(s))));
    }
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule sprint = Rule.named("Sprint")
        .when(SPEED.above(values.get("sprint")))
        .forDuration(Math.round(values.get("sprintSeconds") * SECOND))
        .endWhen(SPEED.below(4));
    Rule recovered = Rule.named("Recovered")
        .when(after(sprint).within(Math.round(values.get("recoveryMinutes") * 60 * SECOND))
            .and(HR.below(values.get("recoveredHr"))));
    Rule sessionDone = Rule.named("SessionDone").when(count(sprint).above(values.get("sprints")));
    return new Rule[] {sprint, recovered, sessionDone};
  }

  /**
   * The example's listener, noting each recovery time on the bridge's Lifecycle, to which it
   * passes every callback on.
   */
  @Override
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    Detector detector = Detector.builder().rules(rules).listener(new EventListener() {
      private long lastSprintEnd;

      @Override public void onPending(Event e) {
        lifecycle.onPending(e);
      }

      @Override public void onConfirmed(Event e) {
        lifecycle.onConfirmed(e);
        if (e.name().equals("Recovered")) {
          lifecycle.note(e, (e.start() - lastSprintEnd) / SECOND + " s");
        }
      }

      @Override public void onEnded(Event e) {
        lifecycle.onEnded(e);
        if (e.name().equals("Sprint")) lastSprintEnd = e.end().getAsLong();
      }

      @Override public void onDiscarded(Event e) {
        lifecycle.onDiscarded(e);
      }
    }).build();
    for (Sample sample : samples) lifecycle.push(detector, sample);
    detector.close();
  }
}
