package io.github.romanhaendler.rbed;

import static io.github.romanhaendler.rbed.Conditions.count;
import static io.github.romanhaendler.rbed.Conditions.totalDuration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.Differential.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Snapshot twins for windows and derived values: moving averages (warm-up, leaving Samples,
 * Retention Limit drops), rate, age, arithmetic, running Aggregates and watched Expressions.
 * Each case takes a Snapshot at every moment of the input.
 */
class SnapshotWindowsTest {
  private static final Channel A = Channel.named("a");
  private static final Channel B = Channel.named("b");

  private static Result everyMoment(Consumer<Detector.Builder> config, List<Expression> watched,
      List<Op> ops) {
    Map<String, Integer> stats = new TreeMap<>();
    for (int k = 0; k <= ops.size(); k++) {
      Result r = Differential.compareRestoredAt(config, config, watched, ops, k);
      if (r.difference() != null) {
        throw new AssertionError("Snapshot after " + k + " ops\n" + r.difference().report());
      }
      r.stats().forEach((name, n) -> stats.merge(name, n, Integer::sum));
    }
    return new Result(null, true, ops.size(), stats);
  }

  /** Values with a wide spread of magnitudes, at irregular times, with silent stretches. */
  private static List<Op> noisy(long seed, int n) {
    Random rnd = new Random(seed);
    List<Op> ops = new ArrayList<>();
    long t = 0;
    for (int i = 0; i < n; i++) {
      t += 1 + rnd.nextInt(300);
      if (rnd.nextInt(8) == 0) {
        ops.add(Op.advance(t));
        continue;
      }
      double a = (rnd.nextInt(3) == 0 ? 1e15 * (rnd.nextBoolean() ? 1 : -1) : 0) + rnd.nextDouble() * 20;
      if (rnd.nextInt(3) > 0) {
        ops.add(Op.push(t, "a", a, "b", rnd.nextDouble() * 10));
      } else {
        ops.add(Op.push(t, "a", a));
      }
    }
    return ops;
  }

  @Test
  void windowsRateAgeAndExpressionsOfAnEventInProgress() {
    ChannelExpression avgS = A.averageOverSamples(4);
    ChannelExpression avgD = A.averageOverDuration(700);
    Rule hot = Rule.named("Hot").when(A.above(8)).forDuration(150).aggregate(A, B);
    Rule smooth = Rule.named("Smooth").when(avgS.above(5)).endWhen(avgD.below(3)).aggregate(A);
    Rule fast = Rule.named("Fast").when(A.rate().above(0)).forSamples(2);
    Rule silent = Rule.named("Silent").when(A.age().above(250));
    List<Expression> watched = List.of(avgS, avgD, A.rate(), A.age(), B.age(), avgS.minus(avgD),
        A.abs().times(2).plus(B), count(hot), totalDuration(smooth), B.averageOverSamples(2));
    Consumer<Detector.Builder> config = b -> b.rules(hot, smooth, fast, silent).watch(
        watched.toArray(new Expression[0]));
    int ended = 0, watchedValues = 0;
    for (long seed = 1; seed <= 12; seed++) {
      Result r = everyMoment(config, watched, noisy(seed, 60));
      ended += r.stats().getOrDefault("ended", 0);
      watchedValues += r.stats().getOrDefault("watchedValue", 0);
    }
    assertTrue(ended > 0 && watchedValues > 0, "vacuous");
  }

  @Test
  void aPulseChannelAbsentFromTheLatestSampleIsStillAbsentAfterARestore() {
    Channel p = Channel.pulse("p");
    Rule r = Rule.named("P").when(p.above(0.5)).aggregate(A);
    Rule n = Rule.named("NotP").when(p.above(0.5).not());
    List<Expression> watched = List.of(p, p.plus(1), p.age(), p.rate());
    Consumer<Detector.Builder> config = b -> b.rules(r, n).watch(watched.toArray(new Expression[0]));
    List<Op> ops = List.of(Op.push(10, "a", 1.0, "p", 1.0), Op.push(20, "a", 2.0),
        Op.push(30, "a", 3.0, "p", 0.0), Op.push(40, "a", 4.0), Op.advance(50),
        Op.push(60, "a", 5.0, "p", 2.0));
    everyMoment(config, watched, ops);
  }

  @Test
  void aWindowAtTheRetentionLimitDropsAndWarnsExactlyAsTheOriginal() {
    ChannelExpression avgD = A.averageOverDuration(5_000);
    Rule r = Rule.named("R").when(avgD.above(0.5)).aggregate(A);
    Consumer<Detector.Builder> config = b -> b.retentionLimit(8).rules(r).watch(avgD);
    List<Op> ops = new ArrayList<>();
    for (int i = 1; i <= 40; i++) ops.add(Op.push(i * 100L, "a", i % 3 == 0 ? 0.0 : 1.0 + i));
    Result result = everyMoment(config, List.of(avgD), ops);
    // The original warns once per run (compareRestoredAt runs it to the end); a twin restored
    // after the drop starts and must not warn again, and one restored before must warn once.
    assertEquals(ops.size() + 1, result.stats().get("warning"));
  }

  @Test
  void compensatedSumsSurviveARestoreBitForBitOverLongRuns() {
    ChannelExpression avgS = A.averageOverSamples(5);
    ChannelExpression avgD = A.averageOverDuration(450);
    Consumer<Detector.Builder> config = b -> b.rules(Rule.named("X").when(avgS.above(0))).watch(avgS, avgD);
    // huge values entering and leaving next to tiny ones, so that naive summation drifts
    double[] pattern = {1e16, 0.1, 1.0, -1e16, 0.3, 1e-3, 7e15, -7e15};
    List<Op> ops = new ArrayList<>();
    for (int i = 0; i < 220; i++) {
      ops.add(Op.push(100L * (i + 1), "a", pattern[i % pattern.length] + (i % 5) * 1e-7));
    }
    Result r = everyMoment(config, List.of(avgS, avgD), ops);
    assertTrue(r.stats().getOrDefault("watchedValue", 0) > 0);
  }
}
