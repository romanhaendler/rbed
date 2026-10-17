package io.github.romanhaendler.rbed;

import static io.github.romanhaendler.rbed.Conditions.after;
import static io.github.romanhaendler.rbed.Conditions.count;
import static io.github.romanhaendler.rbed.Conditions.latest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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

  @Test
  void aWindowThatNeverForgetsIsUnbounded() {
    MemoryHorizon never = new MemoryHorizon(Long.MAX_VALUE, 0);
    assertEquals(never,
        horizon(new Rule[] {Rule.named("A").when(X.averageOverDuration(Long.MAX_VALUE).above(1))}));
    assertEquals(never, horizon(new Rule[] {Rule.named("A").when(X.above(1))},
        Y.averageOverDuration(Long.MAX_VALUE)));
    assertEquals(never,
        horizon(new Rule[] {Rule.named("A").when(X.above(1)).forDuration(Long.MAX_VALUE)}));
    Rule a = Rule.named("A").when(X.above(1));
    assertEquals(never, horizon(new Rule[] {a}, count(a).within(Long.MAX_VALUE)));
    // no gap limit, no window on after(...): nothing to report
    assertEquals(new MemoryHorizon(0, 0), horizon(new Rule[] {a,
        Rule.named("B").when(after(a)).maxGap(Long.MAX_VALUE)}));
  }

  /**
   * A construct that takes a duration or a count must tell the Memory Horizon. The table lists
   * every public method of the building blocks that takes one; a new such method fails the first
   * assertion until it is added here, and a construct that forgets to report fails the second.
   */
  @Test
  void everyConstructThatTakesADurationOrACountReportsItInTheMemoryHorizon() {
    Rule a = Rule.named("A").when(X.above(1));
    Map<String, MemoryHorizon> reported = new TreeMap<>();
    reported.put("After.within", horizon(new Rule[] {a,
        Rule.named("B").when(after(a).within(7_000))}));
    reported.put("ChannelExpression.averageOverDuration",
        horizon(new Rule[] {Rule.named("B").when(X.averageOverDuration(7_000).above(1))}));
    reported.put("ChannelExpression.averageOverSamples",
        horizon(new Rule[] {Rule.named("B").when(X.averageOverSamples(7).above(1))}));
    reported.put("EventCount.within", horizon(new Rule[] {a,
        Rule.named("B").when(count(a).within(7_000).above(1))}));
    reported.put("Rule.forDuration",
        horizon(new Rule[] {Rule.named("B").when(X.above(1)).forDuration(7_000)}));
    reported.put("Rule.forSamples",
        horizon(new Rule[] {Rule.named("B").when(X.above(1)).forSamples(7)}));
    reported.put("Rule.maxGap",
        horizon(new Rule[] {Rule.named("B").when(X.above(1)).maxGap(7_000)}));

    Set<String> found = new TreeSet<>();
    for (Class<?> type : List.of(After.class, ChannelExpression.class, Channel.class,
        Condition.class, Conditions.class, EventCount.class, Expression.class, Rule.class)) {
      for (Method m : type.getDeclaredMethods()) {
        if (!Modifier.isPublic(m.getModifiers())) continue;
        for (Class<?> p : m.getParameterTypes()) {
          if (p == long.class || p == int.class || p == Duration.class) {
            found.add(type.getSimpleName() + "." + m.getName());
          }
        }
      }
    }
    assertEquals(reported.keySet(), found);
    reported.forEach((construct, horizon) -> assertNotEquals(new MemoryHorizon(0, 0), horizon,
        construct));
  }
}
