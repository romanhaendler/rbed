package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.during;
import static rbed.Conditions.latest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The crane story: a pallet is lifted and set down, then a container heavier than the crane's
 * rated load is lifted (Overload during the lift), partly set down (half of its weight is
 * released), and set down completely. Weight is sampled every 500 ms.
 */
class CraneScenarioTest {
  static final Channel RATED_LOAD = Channel.named("ratedLoad");
  static final Channel WEIGHT = Channel.named("weight");

  static double weightAt(long t) {
    if (t < 1500) return 0;      // hook empty
    if (t < 6500) return 1200;   // pallet
    if (t < 8500) return 10;     // set down, empty hook swinging
    if (t < 11500) return 6000;  // container
    if (t < 12500) return 2500;  // part of it set down
    return 0;
  }

  /** Classifies a lifted load by its mean weight. */
  static String load(Event lift) {
    return lift.aggregates(WEIGHT).mean() < 1500 ? "pallet" : "container";
  }

  @Test
  void liftsOverloadAndSetDownsWithClassifiedLoads() {
    List<String> pickedUp = new ArrayList<>();
    Rule loadLifted = Rule.named("LoadLifted")
        .when(WEIGHT.above(500)).forDuration(Duration.ofSeconds(2))
        .endWhen(WEIGHT.below(50)).forDuration(Duration.ofSeconds(1))
        .maxGap(Duration.ofSeconds(5));
    Rule overload = Rule.named("Overload")
        .when(WEIGHT.above(RATED_LOAD).and(during(loadLifted)))
        .forSamples(2);
    Rule partialSetDown = Rule.named("PartialSetDown")
        .when(during(loadLifted)
            .and(WEIGHT.below(latest(loadLifted).max(WEIGHT).times(0.5)))
            .and(WEIGHT.above(50)))  // still a load on the hook
        .forSamples(2);
    Detector crane = Detector.builder()
        .rules(loadLifted, overload, partialSetDown)
        .listener(new EventListener() {
          @Override public void onConfirmed(Event e) {
            pickedUp.add(e.name() + "@" + e.start());
          }
        })
        .build();

    List<Sample> series = new ArrayList<>();
    for (long t = 0; t <= 14000; t += 500) {
      Map<Channel, Double> values = new HashMap<>(Map.of(WEIGHT, weightAt(t)));
      if (t == 0) values.put(RATED_LOAD, 5000.0); // sent once, held from then on
      series.add(new Sample(t, values));
    }
    List<Event> events = crane.detect(series);

    assertEquals(List.of("LoadLifted@1500", "LoadLifted@8500", "Overload@10500",
        "PartialSetDown@11500"), pickedUp);
    assertEquals(List.of(
            "LoadLifted 1500..6500 pallet",
            "Overload 10500..11500",
            // below half of the 6000 kg maximum from 11500 ms until fully set down
            "PartialSetDown 11500..12500",
            // mean of 6 Samples at 6000 kg and 2 at 2500 kg: 5125 kg
            "LoadLifted 8500..12500 container"),
        events.stream()
            .map(e -> e.name() + " " + e.start() + ".." + e.end().getAsLong()
                + (e.is(loadLifted) ? " " + load(e) : ""))
            .toList());
  }
}
