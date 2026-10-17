package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.ReplayHarness.Original;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The Replay Point is not only exact but close to optimal: for representative Rule sets and every
 * stop, the latest start that still reproduces the continuation is found by trying every moment
 * after the Replay Point, and the Replay Point may be at most {@link #SLACK} moments earlier. The
 * slack is the one moment in which a Rule's run just ended, whose end the Replay Point still
 * keeps out of a replay, plus the gaps between Samples where a Condition reads the previous one.
 */
class ReplayPointOptimalityTest {
  /** Moments (Samples and advances) the Replay Point may lie before the latest valid start. */
  static final int SLACK = 3;
  private static final int CONTINUATIONS = 6;
  private static final Channel A = Channel.named("a");
  private static final Channel B = Channel.named("b");
  private static final Channel P = Channel.pulse("p");

  /** Samples with a drifting a and a b, a pulse now and then, and some advances. */
  static List<Op> signal(long seed, int n, long from) {
    SplittableRandom r = new SplittableRandom(seed);
    List<Op> ops = new ArrayList<>();
    long t = from;
    double a = 5;
    for (int i = 0; i < n; i++) {
      t += 10 + r.nextInt(30);
      if (r.nextInt(8) == 0) {
        ops.add(Op.advance(t));
        continue;
      }
      a = r.nextInt(5) == 0 ? r.nextInt(11) : Math.max(0, Math.min(10, a + r.nextInt(5) - 2));
      ops.add(r.nextInt(4) == 0
          ? Op.push(t, "a", a, "b", (double) r.nextInt(8), "p", 1.0)
          : Op.push(t, "a", a, "b", (double) r.nextInt(8)));
    }
    return ops;
  }

  /**
   * Worst lag, in moments, between the Replay Point after the first {@code stop} ops of the
   * prefix and the latest start that reproduces many different continuations (an oracle that
   * knows one future could pick a later start by luck; one that must serve all of them cannot).
   */
  static int lag(Consumer<Detector.Builder> config, List<Expression> watched, List<Op> prefix,
      int stop, long seed) {
    long end = prefix.get(stop - 1).timestamp();
    List<Original> originals = new ArrayList<>();
    for (int j = 0; j < CONTINUATIONS; j++) {
      List<Op> ops = new ArrayList<>(prefix.subList(0, stop));
      ops.addAll(signal(seed * 31 + j, 25, end));
      originals.add(ReplayHarness.run(config, watched, ops));
    }
    Original first = originals.get(0);
    OptionalLong point = first.points().get(stop);
    if (point.isEmpty() || first.dropped().get(stop) > 0) return 0;
    long from = point.getAsLong();
    List<Long> moments = new ArrayList<>();
    for (int i = 0; i < stop; i++) {
      Op op = prefix.get(i);
      if (first.accepted().get(i + 1) && op.kind() != Op.Kind.CLOSE && op.timestamp() > from) {
        moments.add(op.timestamp());
      }
    }
    for (int k = moments.size() - 1; k >= 0; k--) {
      boolean works = true;
      for (Original o : originals) works &= ReplayHarness.replay(o, stop, moments.get(k)) == null;
      if (works) return k + 1;
    }
    return 0;
  }

  private static void assertClose(String what, Consumer<Detector.Builder> config,
      List<Expression> watched) {
    int worst = 0;
    for (long seed = 1; seed <= 4; seed++) {
      List<Op> prefix = signal(seed, 40, 0);
      for (int stop = 10; stop <= prefix.size(); stop++) {
        worst = Math.max(worst, lag(config, watched, prefix, stop, seed));
      }
    }
    System.out.println("replay point optimality: " + what + " worst lag " + worst);
    assertTrue(worst <= SLACK, what + ": the Replay Point is " + worst
        + " moments earlier than the latest start that works");
  }

  /** Rules watch what they read, so a difference in any of it shows at once. */
  private static Consumer<Detector.Builder> with(List<Expression> watched, Rule... rules) {
    return b -> b.rules(rules).watch(watched.toArray(Expression[]::new));
  }

  @Test
  void thresholdsDurationsAndSampleCounts() {
    Rule hot = Rule.named("Hot").when(A.above(6)).forSamples(2).forDuration(20);
    Rule cold = Rule.named("Cold").when(A.below(3)).forSamples(2).aggregate(A, B);
    assertClose("durations", with(List.of(A, B), hot, cold), List.of(A, B));
  }

  @Test
  void maxGap() {
    Rule hot = Rule.named("Hot").when(A.above(4)).forSamples(2).maxGap(120).aggregate(A);
    assertClose("maxGap", with(List.of(A), hot), List.of(A));
  }

  @Test
  void movingAveragesRateAndAge() {
    ChannelExpression smooth = A.averageOverSamples(4);
    ChannelExpression slow = B.averageOverDuration(200);
    Rule warm = Rule.named("Warm").when(smooth.above(5)).forSamples(2);
    Rule steep = Rule.named("Steep").when(A.rate().above(0.05)).forSamples(2);
    Rule silent = Rule.named("Silent").when(slow.rate().below(0.0).and(B.age().above(30)));
    List<Expression> watched = List.of(smooth, slow, slow.rate(), A.rate(), B.age());
    assertClose("windows", with(watched, warm, steep, silent), watched);
  }

  @Test
  void eventsAcrossRulesAndLatestAggregates() {
    // no Rule starts when another's Event ends: such Events chain their runs (see ADR-0007)
    Rule hot = Rule.named("Hot").when(A.above(6)).forSamples(2).aggregate(A);
    Rule busy = Rule.named("Busy").when(Conditions.count(hot).within(400).above(1));
    Rule next = Rule.named("Next").when(Conditions.latest(hot).max(A).above(8).and(A.below(5)));
    List<Expression> watched = List.of(Conditions.latest(hot).max(A), Conditions.count(hot),
        Conditions.totalDuration(busy), Conditions.latest(busy).duration());
    assertClose("events", with(watched, hot, busy, next), watched);
  }

  @Test
  void pulseChannels() {
    Rule beat = Rule.named("Beat").when(P.above(0.5)).forSamples(1);
    Rule gap = Rule.named("Gap").when(P.age().above(100));
    List<Expression> watched = List.of(P, P.age(), P.rate());
    assertClose("pulse", with(watched, beat, gap), watched);
  }
}
