package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The README's lifecycle Rule: Overheat while temp is above 80 for a duration. At the default 2 s
 * a long and a short Event are Confirmed and a one-Sample spike is Discarded; from 3 s on the
 * short Event is Discarded, too.
 */
final class EventLifecycle extends Scenario {
  static final Channel TEMP = Channel.named("temp");
  /** temp at 0, 1, 2, ... s: above 80 from 3 to 8 s, from 12 to 14 s, and at 17 s. */
  private static final double[] VALUES = {
    70, 72, 75, 85, 88, 91, 89, 86, 84, 76, 72, 74, 84, 87, 83, 75, 72, 90, 74, 71, 70
  };

  EventLifecycle() {
    super("event-lifecycle", "The Event lifecycle", "s", 1_000, List.of(TEMP),
        List.of(new Parameter("duration", "Duration", 2, 0, 6, 1, "s")),
        """
        Channel temp = Channel.named("temp");

        Rule overheat = Rule.named("Overheat")
            .when(temp.above(80)).forDuration(Duration.ofSeconds(${duration}));
        Detector detector = Detector.builder()
            .rules(overheat)
            .listener(new EventListener() {
              @Override public void onPending(Event e)   { System.out.println("pending at " + e.start()); }
              @Override public void onConfirmed(Event e) { System.out.println("confirmed at " + e.confirmed().getAsLong()); }
              @Override public void onEnded(Event e)     { System.out.println("ended at " + e.end().getAsLong()); }
              @Override public void onDiscarded(Event e) { System.out.println("discarded"); }
            })
            .build();

        double[] values = {70, 72, 75, 85, 88, 91, 89, 86, 84, 76, 72, 74, 84, 87, 83, 75, 72, 90, 74, 71, 70};
        for (int i = 0; i < values.length; i++) {
          detector.push(i * 1_000L, Map.of(temp, values[i]));   // one Sample per second
        }
        detector.close();
        """);
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int i = 0; i < VALUES.length; i++) {
      samples.add(new Sample(i * 1_000L, Map.of(TEMP, VALUES[i])));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    return new Rule[] {
      Rule.named("Overheat").when(TEMP.above(80))
          .forDuration(Math.round(values.get("duration") * 1_000))
    };
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(TEMP, "Overheat", "when >", 80));
  }
}
