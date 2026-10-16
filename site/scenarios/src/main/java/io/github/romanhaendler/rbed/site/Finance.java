package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** TradingSignalsExample: daily closing prices, falling for 300 days and then rising. */
final class Finance extends Scenario {
  static final Channel PRICE = Channel.named("price");
  static final long DAY = 86_400_000;

  Finance() {
    super("finance", "Finance", "d", DAY, List.of(PRICE),
        List.of(
            new Parameter("fast", "Fast average over", 50, 5, 150, 5, "Samples"),
            new Parameter("slow", "Slow average over", 200, 50, 400, 10, "Samples")),
        """
        Channel price = Channel.named("price");

        Rule deathCross = Rule.named("DeathCross").when(
            price.averageOverSamples(${fast}).below(price.averageOverSamples(${slow})));
        Rule goldenCross = Rule.named("GoldenCross").when(
            price.averageOverSamples(${fast}).above(price.averageOverSamples(${slow})));
        Detector detector = Detector.builder()
            .rules(deathCross, goldenCross)
            .build();
        """);
  }

  static double price(double day) {
    return day < 300 ? 200 - day / 3 : 100 + (day - 300) / 2;
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> series = new ArrayList<>();
    for (int d = 0; d < 600; d++) series.add(new Sample(d * DAY, Map.of(PRICE, price(d))));
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    int fast = (int) Math.round(values.get("fast"));
    int slow = (int) Math.round(values.get("slow"));
    Rule deathCross = Rule.named("DeathCross").when(
        PRICE.averageOverSamples(fast).below(PRICE.averageOverSamples(slow)));
    Rule goldenCross = Rule.named("GoldenCross").when(
        PRICE.averageOverSamples(fast).above(PRICE.averageOverSamples(slow)));
    return new Rule[] {deathCross, goldenCross};
  }

  /** The two moving averages, whose crossings the Events are. */
  @Override
  List<Plot> plots(Map<String, Double> values) {
    int fast = (int) Math.round(values.get("fast"));
    int slow = (int) Math.round(values.get("slow"));
    return List.of(
        new Plot("price.averageOverSamples(" + fast + ")", PRICE.averageOverSamples(fast)),
        new Plot("price.averageOverSamples(" + slow + ")", PRICE.averageOverSamples(slow)));
  }
}
