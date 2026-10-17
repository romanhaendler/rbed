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
    // not pinned at the beginning of the input: in the second half of 60 moments, never
    assertTrue(total.pinned() < total.checked() / 4,
        what + ": the Replay Point stays at the first moment " + total);
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
    Rule warm = Rule.named("Warm").when(smooth.above(5)).forSamples(2);
    Rule steep = Rule.named("Steep").when(A.rate().above(0.05)).forSamples(2);
    Rule silent = Rule.named("Silent").when(slow.rate().below(0.0).and(B.age().above(30)));
    List<Expression> watched = List.of(smooth.averageOverSamples(3), slow, A.age(), B.rate());
    checkMany("windows", b -> b.rules(warm, steep, silent).watch(watched.toArray(Expression[]::new)),
        watched, 40, 0, 0.2);
  }

  @Test
  void nestedMovingAverages() {
    ChannelExpression nested = A.averageOverSamples(3).averageOverSamples(2);
    ChannelExpression nestedDuration = B.averageOverDuration(80).averageOverDuration(60);
    Rule up = Rule.named("Up").when(nested.above(5)).forSamples(2);
    Rule down = Rule.named("Down").when(nestedDuration.below(3));
    List<Expression> watched = List.of(nested, nestedDuration.rate());
    checkMany("nested averages", b -> b.rules(up, down).watch(watched.toArray(Expression[]::new)),
        watched, 30, 0, 0.1);
  }

  @Test
  void aPulseDetectorWithMaxGapNeedsThePreviousSample() {
    // the pulse is absent in a time advance, so only the previous Sample tells about the gap
    Rule beat = Rule.named("Beat").when(P.above(0.5)).forSamples(1).maxGap(100);
    checkMany("pulse and maxGap", b -> b.rules(beat), List.of(), 40, 150, 0.3);
  }

  @Test
  void expiredConfirmationTimesDoNotDependOnWhoReadTheCount() {
    // found by the extended run: R2 only reads the count when the pulse is there, so the
    // expired confirmation of R1 used to be dropped later in one Detector than in another
    Rule r1 = Rule.named("R1").when(B.above(10.0));
    Rule r2 = Rule.named("R2").when(P.above(Conditions.count(r1).within(1366L)));
    Outcome o = ReplayHarness.check(b -> b.rules(r1, r2), List.of(),
        List.of(Op.push(5796, "b", 10.000000000000002), Op.push(7977, "b", 0.0, "p", -0.0),
            Op.push(9375, "b", 3.0)));
    assertNull(o.failure(), () -> o.failure().report());
  }

  @Test
  void eventsAcrossRules() {
    Rule hot = Rule.named("Hot").when(A.above(6)).forSamples(2);
    Rule alarm = Rule.named("Alarm").when(Conditions.after(hot).within(300).and(B.above(3)));
    Rule busy = Rule.named("Busy").when(Conditions.count(hot).within(400).above(1));
    Rule fine = Rule.named("Fine").when(Conditions.after(hot).unless(alarm).and(A.below(4)));
    Rule long_ = Rule.named("Long").when(Conditions.totalDuration(hot).above(150));
    List<Expression> watched = List.of(Conditions.count(hot), Conditions.count(busy).within(300),
        Conditions.totalDuration(alarm), Conditions.latest(hot).duration());
    checkMany("events across rules", b -> b.rules(hot, alarm, busy, fine, long_)
        .watch(watched.toArray(Expression[]::new)), watched, 30, 0, 0.1);
  }

  @Test
  void latestAggregatesOfAnEndedEvent() {
    Rule hot = Rule.named("Hot").when(A.above(6)).forSamples(2).aggregate(A);
    Rule next = Rule.named("Next").when(Conditions.latest(hot).max(A).above(8).and(A.below(5)));
    List<Expression> watched =
        List.of(Conditions.latest(hot).mean(B), Conditions.latest(hot).min(A));
    checkMany("latest", b -> b.rules(hot, next).watch(watched.toArray(Expression[]::new)), watched,
        30, 0, 0.1);
  }

  @Test
  void pulseChannels() {
    Rule beat = Rule.named("Beat").when(P.above(0.5)).forSamples(1);
    Rule quiet = Rule.named("Quiet").when(P.rising().not().and(A.above(2))).forDuration(60);
    Expression since = P.age();
    Rule gap = Rule.named("Gap").when(since.above(100));
    checkMany("pulse", b -> b.rules(beat, quiet, gap).watch(since), List.of(since), 30, 0, 0.1);
  }

  @Test
  void historySuppliedAtTheFirstBuild() {
    Rule hot = Rule.named("Hot").when(A.above(6)).forSamples(2);
    Rule after = Rule.named("After").when(Conditions.after(hot).within(500).and(A.below(5)));
    Event old = new Event("Hot", -300, OptionalLong.of(-250), OptionalLong.of(-100), false, false,
        Map.of());
    checkMany("history", b -> b.rules(hot, after).history(List.of(old)), List.of(), 30, 0, 0.1);
  }

  @Test
  void generatedCases() {
    String one = System.getProperty("rbed.diff.seed");
    long first = one != null ? Long.parseLong(one) : FRESH_SEEDS ? System.nanoTime() : 1;
    int cases = one != null ? 1 : CASES;
    Stats total = Stats.NONE;
    for (long seed = first; seed < first + cases; seed++) {
      Scenario s = ScenarioGenerator.generate(seed);
      Scenario.Built built;
      try {
        built = s.build();
      } catch (RuntimeException e) {
        continue;
      }
      Outcome o = ReplayHarness.check(built.config(), built.watched(), s.ops());
      if (o.failure() != null) {
        Scenario small = Differential.shrink(s, c -> {
          try {
            Scenario.Built b = c.build();
            return ReplayHarness.check(b.config(), b.watched(), c.ops()).failure() != null;
          } catch (RuntimeException e) {
            return false;
          }
        });
        Scenario.Built b = small.build();
        Outcome so = ReplayHarness.check(b.config(), b.watched(), small.ops());
        throw new AssertionError("Seed " + seed + " (rerun with -Drbed.diff.seed=" + seed + ")\n"
            + so.failure().report() + "\n\nShrunk scenario:\n" + small.toJava("replay_seed_" + seed));
      }
      total = total.plus(o.stats());
    }
    System.out.println("replay point: " + cases + " cases, " + total);
    if (one == null) {
      assertTrue(total.checked() > total.stops() / 4, "few stops checked: " + total);
      assertTrue(total.teeth() > total.checked() / 20, "replay from one Sample later rarely differs: " + total);
      assertTrue(total.skippedDropped() > 0, "no case dropped entries: " + total);
      assertTrue(total.pinned() < total.checked() / 2, "the Replay Point sits at the first moment: " + total);
    }
  }

  @Test
  void emptyBeforeTheFirstSampleAndInSamplingRateMode() {
    Rule hot = Rule.named("Hot").when(A.above(5));
    Detector d = Detector.builder().rules(hot).build();
    assertTrue(d.replayPoint().isEmpty());
    d.advanceTo(10);
    assertTrue(d.replayPoint().isEmpty());
    d.push(20, Map.of(A, 1.0));
    assertEquals(OptionalLong.of(20), d.replayPoint());
    Detector sampled = Detector.builder().rules(hot).samplingRate(10).build();
    assertTrue(sampled.replayPoint().isEmpty());
    sampled.push(Map.of(A, 9.0));
    sampled.push(Map.of(A, 9.0));
    assertTrue(sampled.replayPoint().isEmpty());
  }

  @Test
  void aRuleThatHasNotSeenWhenStopHoldingReachesBackToIt() {
    Rule run = Rule.named("Run").when(A.above(3)).forSamples(1).endWhen(A.above(7)).forSamples(1);
    Detector d = Detector.builder().rules(run).build();
    d.push(0, Map.of(A, 1.0)); // when is false
    d.push(10, Map.of(A, 5.0)); // Event starts
    d.push(20, Map.of(A, 8.0)); // Event ends on its End Condition at 20; when still holds
    d.push(30, Map.of(A, 8.0));
    d.push(40, Map.of(A, 8.0));
    assertNotNull(d.replayPoint());
    assertEquals(OptionalLong.of(0), d.replayPoint());
    d.push(50, Map.of(A, 1.0)); // when stops holding: free again
    d.push(60, Map.of(A, 1.0));
    assertEquals(OptionalLong.of(60), d.replayPoint());
  }

  @Test
  void aGapInTheDataStartsFreshAtTheGap() {
    Rule hot = Rule.named("Hot").when(A.above(4)).forSamples(3).maxGap(100);
    Detector d = Detector.builder().rules(hot).build();
    d.push(0, Map.of(A, 5.0));
    d.push(10, Map.of(A, 5.0));
    d.push(20, Map.of(A, 5.0)); // Confirmed
    assertEquals(OptionalLong.of(0), d.replayPoint());
    d.push(500, Map.of(A, 1.0)); // the gap Ends the Event at 20, truncated
    assertEquals(OptionalLong.of(500), d.replayPoint());
  }

  @Test
  void aSmallRetentionLimitThatDroppedEntriesIsAPrecondition() {
    // once a window dropped entries, a replay (which drops at other moments) can differ; the
    // harness skips such stops, so show that the difference is real
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
