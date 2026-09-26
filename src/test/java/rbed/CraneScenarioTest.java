package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;
import static rbed.Conditions.during;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The crane story: a pallet is lifted and set down, then a container heavier than the crane's
 * rated load is lifted (Overload during the lift) and set down. Weight is sampled every 500 ms.
 */
class CraneScenarioTest {

  static double weightAt(long t) {
    if (t < 1500) return 0;      // hook empty
    if (t < 6500) return 1200;   // pallet
    if (t < 8500) return 10;     // set down, empty hook swinging
    if (t < 12500) return 6000;  // container
    return 0;
  }

  @Test
  void liftsOverloadAndSetDownsWithClassifiedLoads() {
    List<String> pickedUp = new ArrayList<>();
    Detector crane = Detector.builder()
        .rule("LoadLifted", r -> r
            .when(channel("weight").above(500)).forDuration(Duration.ofSeconds(2))
            .endWhen(channel("weight").below(50)).forDuration(Duration.ofSeconds(1))
            .maxGap(Duration.ofSeconds(5))
            .attribute("load", e -> e.aggregates("weight").mean() < 1500 ? "pallet" : "container"))
        .rule("Overload", r -> r
            .when(channel("weight").above(channel("ratedLoad")).and(during("LoadLifted")))
            .forSamples(2))
        .listener(new EventListener() {
          @Override public void onConfirmed(Event e) {
            pickedUp.add(e.name() + "@" + e.start());
          }
        })
        .build();

    List<Sample> series = new ArrayList<>();
    for (long t = 0; t <= 14000; t += 500) {
      Map<String, Double> values = new HashMap<>(Map.of("weight", weightAt(t)));
      if (t == 0) values.put("ratedLoad", 5000.0); // sent once, held from then on
      series.add(new Sample(t, values));
    }
    List<Event> events = crane.detect(series);

    assertEquals(List.of("LoadLifted@1500", "LoadLifted@8500", "Overload@10500"), pickedUp);
    assertEquals(List.of(
            "LoadLifted 1500..6500 {load=pallet}",
            "Overload 10500..12500 {}",
            "LoadLifted 8500..12500 {load=container}"),
        events.stream()
            .map(e -> e.name() + " " + e.start() + ".." + e.end().getAsLong() + " " + e.attributes())
            .toList());
  }
}
