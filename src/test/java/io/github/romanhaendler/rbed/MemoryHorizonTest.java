package io.github.romanhaendler.rbed;

import static io.github.romanhaendler.rbed.Conditions.after;
import static io.github.romanhaendler.rbed.Conditions.count;
import static io.github.romanhaendler.rbed.Conditions.latest;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MemoryHorizonTest {
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");

  static MemoryHorizon horizon(Rule[] rules, Expression... watched) {
    return Detector.builder().rules(rules).watch(watched).build().memoryHorizon();
  }

  @Test
  void withoutWindowedConstructsBothAreZero() {
    Rule plain = Rule.named("Plain").when(X.above(1));
    assertEquals(new MemoryHorizon(0, 0), horizon(new Rule[] {plain}, Y, count(plain)));
  }

  @Test
  void rulePhasesAndMaxGap() {
    Rule a = Rule.named("A").when(X.above(1)).forDuration(2_000).forSamples(4)
        .endWhen(X.below(0)).forDuration(9_000).forSamples(3).maxGap(5_000);
    assertEquals(new MemoryHorizon(9_000, 4), horizon(new Rule[] {a}));
  }

  @Test
  void maxGapAloneAndSamplesAlone() {
    assertEquals(new MemoryHorizon(7_000, 0),
        horizon(new Rule[] {Rule.named("A").when(X.above(1)).maxGap(7_000)}));
    assertEquals(new MemoryHorizon(0, 6),
        horizon(new Rule[] {Rule.named("A").when(X.above(1)).forSamples(6)}));
  }

  @Test
  void conditionsInsideRules() {
    Rule a = Rule.named("A").when(X.above(1));
    Rule b = Rule.named("B").when(
        X.averageOverSamples(8).above(1)
            .and(Y.averageOverDuration(30_000).below(5))
            .and(after(a).within(40_000))
            .and(count(a).within(50_000).above(2)));
    assertEquals(new MemoryHorizon(50_000, 8), horizon(new Rule[] {a, b}));
  }

  @Test
  void unboundedAfterAndCountAddNothing() {
    Rule a = Rule.named("A").when(X.above(1));
    Rule b = Rule.named("B").when(after(a).and(count(a).above(1)));
    assertEquals(new MemoryHorizon(0, 0), horizon(new Rule[] {a, b}));
  }

  @Test
  void watchedAndNestedExpressions() {
    Rule a = Rule.named("A").when(X.above(1));
    Expression nested = X.averageOverSamples(12).plus(Y.averageOverDuration(70_000).abs())
        .minus(count(a).within(60_000)).dividedBy(latest(a).duration());
    assertEquals(new MemoryHorizon(70_000, 12), horizon(new Rule[] {a}, nested));
  }

  @Test
  void largestAcrossRulesAndWatchedWins() {
    Rule a = Rule.named("A").when(X.above(1)).forDuration(100).forSamples(20);
    Rule b = Rule.named("B").when(X.above(1)).forDuration(300).forSamples(2);
    assertEquals(new MemoryHorizon(500, 20),
        horizon(new Rule[] {a, b}, Y.averageOverDuration(500), Y.averageOverSamples(10)));
  }
}
