package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.during;
import static io.github.romanhaendler.rbed.Conditions.latest;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CraneScenarioTest: a pallet is lifted and set down, then a container heavier than the rated
 * load is lifted, partly set down and set down completely. Weight every 500 ms.
 */
final class Cranes extends Scenario {
  static final Channel RATED_LOAD = Channel.named("ratedLoad");
  static final Channel WEIGHT = Channel.named("weight");

  Cranes() {
    super("cranes", "Cranes", "s", 1_000, List.of(WEIGHT, RATED_LOAD),
        List.of(
            new Parameter("lift", "Lifted above", 500, 100, 2000, 50, "kg"),
            new Parameter("liftSeconds", "Lifted for", 2, 1, 5, 1, "s"),
            new Parameter("overloadSamples", "Overload for", 2, 1, 6, 1, "Samples"),
            new Parameter("partial", "Partial set-down below", 0.5, 0.1, 0.9, 0.05, "× max")),
        """
        Channel ratedLoad = Channel.named("ratedLoad");
        Channel weight = Channel.named("weight");

        Rule loadLifted = Rule.named("LoadLifted")
            .when(weight.above(${lift})).forDuration(Duration.ofSeconds(${liftSeconds}))
            .endWhen(weight.below(50)).forDuration(Duration.ofSeconds(1))
            .maxGap(Duration.ofSeconds(5));
        Rule overload = Rule.named("Overload")
            .when(weight.above(ratedLoad).and(during(loadLifted)))
            .forSamples(${overloadSamples});
        Rule partialSetDown = Rule.named("PartialSetDown")
            .when(during(loadLifted)
                .and(weight.below(latest(loadLifted).max(weight).times(${partial})))
                .and(weight.above(50)))  // still a load on the hook
            .forSamples(2);
        Detector crane = Detector.builder()
            .rules(loadLifted, overload, partialSetDown)
            .build();

        // The listener classifies a lifted load by its mean weight
        String load(Event lift) {
          return lift.aggregates(weight).mean() < 1500 ? "pallet" : "container";
        }
        """);
  }

  static double weightAt(long t) {
    if (t < 1500) return 0;      // hook empty
    if (t < 6500) return 1200;   // pallet
    if (t < 8500) return 10;     // set down, empty hook swinging
    if (t < 11500) return 6000;  // container
    if (t < 12500) return 2500;  // part of it set down
    return 0;
  }

  @Override
  List<Sample> samples() {
    List<Sample> series = new ArrayList<>();
    for (long t = 0; t <= 14000; t += 500) {
      Map<Channel, Double> values = new HashMap<>(Map.of(WEIGHT, weightAt(t)));
      if (t == 0) values.put(RATED_LOAD, 5000.0); // sent once, held from then on
      series.add(new Sample(t, values));
    }
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule loadLifted = Rule.named("LoadLifted")
        .when(WEIGHT.above(values.get("lift")))
        .forDuration(Duration.ofMillis(Math.round(values.get("liftSeconds") * 1000)))
        .endWhen(WEIGHT.below(50)).forDuration(Duration.ofSeconds(1))
        .maxGap(Duration.ofSeconds(5));
    Rule overload = Rule.named("Overload")
        .when(WEIGHT.above(RATED_LOAD).and(during(loadLifted)))
        .forSamples((int) Math.round(values.get("overloadSamples")));
    Rule partialSetDown = Rule.named("PartialSetDown")
        .when(during(loadLifted)
            .and(WEIGHT.below(latest(loadLifted).max(WEIGHT).times(values.get("partial"))))
            .and(WEIGHT.above(50)))
        .forSamples(2);
    return new Rule[] {loadLifted, overload, partialSetDown};
  }

  @Override
  String note(Event event, Map<String, Double> values) {
    if (!event.name().equals("LoadLifted")) return null;
    return event.aggregates(WEIGHT).mean() < 1500 ? "pallet" : "container";
  }
}
