package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.ReplayHarness.Outcome;
import io.github.romanhaendler.rbed.ReplayHarness.Stats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The Replay Point: for every stop point of representative Rule sets and of generated cases, a
 * replay from it, with the History of the Events Ended before it, never differs from the
 * uninterrupted run from the stop on; and a replay from one Sample later does differ somewhere.
 */
class ReplayPointTest {
  private static final int CASES = Integer.getInteger("rbed.diff.cases", 3000);
  private static final boolean FRESH_SEEDS = Boolean.getBoolean("rbed.diff.fresh");
  private static final Channel A = Channel.named("a");
  private static final Channel B = Channel.named("b");
  private static final Channel P = Channel.pulse("p");

  /** Mostly regular Samples of a, b and p with dips, jumps, gaps and now and then an advance. */
  private static List<Op> signal(long seed, int n, long step, long gapAbove, double advanceShare) {
    SplittableRandom r = new SplittableRandom(seed);
    List<Op> ops = new ArrayList<>();
    long t = 0;
    double a = 5;
    for (int i = 0; i < n; i++) {
      t += 1 + r.nextLong(step);
      if (gapAbove > 0 && r.nextInt(12) == 0) t += gapAbove + r.nextLong(gapAbove);
      if (r.nextDouble() < advanceShare) {
        ops.add(Op.advance(t));
        continue;
      }
      a = r.nextInt(4) == 0 ? r.nextInt(11) : Math.max(0, Math.min(10, a + r.nextInt(5) - 2));
      Object[] kv = r.nextInt(3) == 0
          ? new Object[] {"a", a, "b", (double) r.nextInt(8), "p", (double) r.nextInt(3)}
          : new Object[] {"a", a, "b", (double) r.nextInt(8)};
      ops.add(Op.push(t, kv));
    }
    return ops;
  }

  private static Stats check(String what, Consumer<Detector.Builder> config,
      List<Expression> watched, List<Op> ops) {
    Outcome o = ReplayHarness.check(config, watched, ops);
    assertNull(o.failure(), () -> what + ": " + o.failure().report());
    Stats s = o.stats();
    assertTrue(s.checked() > ops.size() / 2, what + ": few stops checked " + s);
    return s;
  }

  private static Stats checkMany(String what, Consumer<Detector.Builder> config,
      List<Expression> watched, long step, long gapAbove, double advanceShare) {
    Stats total = Stats.NONE;
    for (long seed = 1; seed <= 12; seed++) {
      total = total.plus(check(what + " seed " + seed, config, watched,
          signal(seed, 60, step, gapAbove, advanceShare)));
    }
    assertTrue(total.teeth() > 0, what + ": replaying from one Sample later never differs " + total);
    assertTrue(total.earlier() > 0, what + ": the Replay Point is always the latest moment " + total);
    return total;
  }

  @Test
  void ruleWhoseWhenStillHoldsAfterItsEventEnded() {
    // The Event ends on its End Condition while a > 3 still holds: a fresh Detector would start
    // a new Event at the first replayed Sample.
    Rule run = Rule.named("Run").when(A.above(3)).forDuration(20).endWhen(A.above(7)).forDuration(30);
    checkMany("end condition", b -> b.rules(run), List.of(), 25, 0, 0.1);
  }

  @Test
  void durationsAndSampleCounts() {
    Rule hot = Rule.named("Hot").when(A.above(6)).forSamples(3).forDuration(40);
    Rule cold = Rule.named("Cold").when(A.below(3)).forSamples(2).aggregate(A, B);
    checkMany("durations", b -> b.rules(hot, cold), List.of(), 30, 0, 0.1);
  }

  @Test
  void maxGapAndDataGaps() {
    Rule hot = Rule.named("Hot").when(A.above(4)).forSamples(2).maxGap(120).aggregate(A);
    Rule slow = Rule.named("Slow").when(B.above(2)).forDuration(100).endWhen(B.below(1)).maxGap(300);
    checkMany("maxGap", b -> b.rules(hot, slow), List.of(), 60, 150, 0.15);
  }

  @Test
  void movingAveragesRateAndAge() {
    ChannelExpression smooth = A.averageOverSamples(4);
    ChannelExpression slow = B.averageOverDuration(200);
    Rule warm = Rule.named("Warm").when(A.averageOverDuration(522).above(-1.0));
    Consumer<Detector.Builder> config = b -> b.rules(warm).retentionLimit(1);
    List<Op> ops = List.of(Op.push(4108, "a", 8.0), Op.push(4158, "a", 11.0), Op.push(4258, "a", 11.0));
    ReplayHarness.Original o = ReplayHarness.run(config, List.of(), ops);
    assertTrue(o.dropped().get(2) > 0);
    boolean differs = false;
    for (int stop = 1; stop <= ops.size(); stop++) {
      if (o.dropped().get(stop) > 0 && o.points().get(stop).isPresent()
          && ReplayHarness.replay(o, stop, o.points().get(stop).getAsLong()) != null) {
        differs = true;
      }
    }
    assertTrue(differs, "expected a stop where the dropped entries show in a replay");
  }
}
