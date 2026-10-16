package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.after;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's restart: a machine's service mode, one Sample a day for 200 days, with maintenance
 * on days 10 and 140. The Detector is restarted on a chosen day; with the Events Ended before the
 * restart as History, OverdueService still knows the last maintenance.
 */
final class History extends Scenario {
  static final Channel SERVICE_MODE = Channel.named("serviceMode");
  static final long DAY = 86_400_000;

  History() {
    super("history", "Restarts: History", "d", DAY, List.of(SERVICE_MODE),
        List.of(
            new Parameter("restart", "Restart on day", 60, 20, 180, 10, "d"),
            Parameter.choice("history", "After the restart", 0, List.of(
                new Option("With History", "\n    .history(stored)"),
                new Option("Without History", "")))),
        """
        Channel serviceMode = Channel.named("serviceMode");
        Rule maintenance = Rule.named("Maintenance").when(serviceMode.above(0.5));
        Rule overdueService = Rule.named("OverdueService")
            .when(after(maintenance).within(Duration.ofDays(90)).not());

        // before the restart: store the Ended Events (in a database; here in a list)
        List<Event> stored = new ArrayList<>();
        Detector before = Detector.builder()
            .rules(maintenance, overdueService)
            .listener(new EventListener() {
              @Override public void onEnded(Event e) { stored.add(e); }
            })
            .build();
        // ... one Sample a day until day ${restart}, then
        before.close();

        // after the restart
        Detector restarted = Detector.builder()
            .rules(maintenance, overdueService)${history}
            .build();
        // ... one Sample a day from day ${restart} on
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (long d = 0; d <= 200; d++) {
      samples.add(new Sample(d * DAY, Map.of(SERVICE_MODE, d == 10 || d == 140 ? 1.0 : 0.0)));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule maintenance = Rule.named("Maintenance").when(SERVICE_MODE.above(0.5));
    Rule overdueService = Rule.named("OverdueService")
        .when(after(maintenance).within(90 * DAY).not());
    return new Rule[] {maintenance, overdueService};
  }

  /**
   * Pushes the Samples before the restart day to one Detector and closes it, then the rest to a
   * new one, which gets every Event Ended so far as History unless the reader chose without.
   */
  @Override
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    long restart = Math.round(values.get("restart")) * DAY;
    Detector detector = Detector.builder().rules(rules).listener(lifecycle).build();
    for (Sample sample : samples) {
      if (sample.timestamp() == restart) {
        detector.close();
        Detector.Builder builder = Detector.builder().rules(rules).listener(lifecycle);
        if (values.get("history") == 0) builder.history(List.copyOf(lifecycle.ended));
        detector = builder.build();
        lifecycle.restart = restart;
      }
      lifecycle.push(detector, sample);
    }
    detector.close();
  }

  @Override
  List<Window> windows(Map<String, Double> values) {
    return List.of(new Window("Maintenance", 90 * DAY));
  }
}
