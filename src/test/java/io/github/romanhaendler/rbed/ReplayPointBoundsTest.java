package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * On a long steady input the Replay Point keeps up with the stop: for every construct it stays
 * within a bound of the latest moment, however long the input has run. The bounds are what the
 * construct itself needs (its window, its pulse spacing, the Samples it compares) plus the length
 * of the Events and runs the input produces, which are short by design.
 */
class ReplayPointBoundsTest {
  private static final Channel A = Channel.named("a");
  private static final Channel B = Channel.named("b");
  private static final Channel P = Channel.pulse("p");
  /** One Sample every 10 ms; a triangle wave of period 10 Samples: 0, 2, ..., 8, 10, 8, ..., 2. */
  private static final long STEP = 10;
  private static final int SAMPLES = 400;

  static double triangle(int i) {
    int k = i % 10;
    return (k <= 5 ? k : 10 - k) * 2.0;
  }

  /** The Samples of a (triangle), b (a shifted copy) and a pulse every seventh Sample. */
  private static void feed(Detector d, int from, int to, java.util.function.IntPredicate skip) {
    for (int i = from; i < to; i++) {
      if (skip.test(i)) continue;
      long t = i * STEP;
      Map<Channel, Double> values = i % 7 == 0
          ? Map.of(A, triangle(i), B, triangle(i + 3), P, 1.0)
          : Map.of(A, triangle(i), B, triangle(i + 3));
      d.push(t, values);
    }
  }

  /**
   * The largest distance in milliseconds between the latest Sample and the Replay Point, over all
   * stops in the second half of the input (the first half warms everything up), also checked
   * against the start of the input: the Replay Point has moved on.
   */
  private static long lag(Consumer<Detector.Builder> config, java.util.function.IntPredicate skip) {
    Detector.Builder b = Detector.builder();
    config.accept(b);
    Detector d = b.build();
    long worst = 0;
    for (int i = 0; i < SAMPLES; i++) {
      feed(d, i, i + 1, skip);
      if (i < SAMPLES / 2 || skip.test(i)) continue;
      long point = d.replayPoint().getAsLong();
      worst = Math.max(worst, i * STEP - point);
    }
    return worst;
  }

  private static void assertWithin(String what, long boundMillis,
      Consumer<Detector.Builder> config) {
    long lag = lag(config, i -> false);
    System.out.println("replay point bounds: " + what + " lag " + lag + " ms (bound " + boundMillis + ")");
    assertTrue(lag <= boundMillis, what + ": the Replay Point is " + lag
        + " ms before the latest Sample, bound " + boundMillis);
  }

  @Test
  void thresholdsWithSampleCountsAndDurations() {
    assertWithin("forSamples", 12 * STEP,
        b -> b.rules(Rule.named("Hot").when(A.above(6)).forSamples(3)));
    assertWithin("forDuration", 12 * STEP,
        b -> b.rules(Rule.named("Hot").when(A.above(6)).forDuration(30)));
  }

  @Test
  void movingAveragesOverSamplesAndOverDuration() {
    // the window, plus the entry that proves a duration window warm, plus the Event in progress
    assertWithin("averageOverSamples(4)", (4 + 10 + 3) * STEP,
        b -> b.rules(Rule.named("Warm").when(A.averageOverSamples(4).above(5))));
    assertWithin("averageOverDuration(40)", 40 + (10 + 3) * STEP,
        b -> b.rules(Rule.named("Warm").when(A.averageOverDuration(40).above(5))));
    // a watched average has no Event: just its window
    ChannelExpression watched = B.averageOverSamples(20);
    assertWithin("watched averageOverSamples(20)", (20 + 3) * STEP,
        b -> b.rules(Rule.named("Hot").when(A.above(9))).watch(watched));
  }

  @Test
  void rateRisingAndAgeReadThePreviousSampleOrPulse() {
    assertWithin("rate", 10 * STEP, b -> b.rules(Rule.named("Fast").when(A.rate().above(100))));
    assertWithin("rising", 10 * STEP, b -> b.rules(Rule.named("Up").when(A.rising())));
    assertWithin("rate of an average", 14 * STEP,
        b -> b.rules(Rule.named("Up").when(A.averageOverSamples(3).rate().above(0))));
    assertWithin("age of a Pulse Channel", 12 * STEP,
        b -> b.rules(Rule.named("Silent").when(P.age().above(50))));
    assertWithin("a Pulse Channel's value", 12 * STEP,
        b -> b.rules(Rule.named("Beat").when(P.above(0.5))));
  }

  @Test
  void maxGapOnlyNeedsThePreviousSample() {
    assertWithin("maxGap", 12 * STEP,
        b -> b.rules(Rule.named("Hot").when(A.above(6)).maxGap(35)));
  }

  @Test
  void anEndConditionAfterWhichWhenStillHolds() {
    // when holds for 7 Samples per period; the Event ends on its End Condition inside that run
    assertWithin("End Condition", 12 * STEP, b -> b.rules(Rule.named("Run").when(A.above(3))
        .forDuration(20).endWhen(A.above(7)).forDuration(10)));
  }

  @Test
  void eventsOfOtherRulesAreRestoredFromHistoryNotReplayed() {
    Rule hot = Rule.named("Hot").when(A.above(6)).forSamples(2);
    assertWithin("count and after", 14 * STEP, b -> b.rules(hot,
        Rule.named("Busy").when(Conditions.count(hot).within(150).above(1)),
        Rule.named("Next").when(Conditions.after(hot).within(300).and(B.above(7)))));
    assertWithin("totalDuration and latest(...).duration()", 14 * STEP,
        b -> b.rules(hot).watch(Conditions.totalDuration(hot), Conditions.latest(hot).duration()));
  }

  @Test
  void latestAggregatesReachBackToTheLatestEvent() {
    Rule hot = Rule.named("Hot").when(A.above(6)).forSamples(2).aggregate(A);
    assertWithin("latest(...).max", 20 * STEP, b -> b.rules(hot,
        Rule.named("Next").when(Conditions.latest(hot).max(A).above(9).and(A.below(5)))));
  }

  @Test
  void aStreamWithGapsKeepsUpToo() {
    List<Integer> gaps = new ArrayList<>();
    for (int i = 0; i < SAMPLES; i += 37) gaps.add(i);
    Rule hot = Rule.named("Hot").when(A.above(6)).forSamples(2).maxGap(25);
    long lag = lag(b -> b.rules(hot, Rule.named("Avg").when(A.averageOverSamples(5).above(5))),
        i -> gaps.contains(i) || gaps.contains(i - 1));
    assertTrue(lag <= 40 * STEP, "lag " + lag);
  }
}
