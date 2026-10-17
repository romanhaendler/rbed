package io.github.romanhaendler.rbed;

import static io.github.romanhaendler.rbed.Conditions.after;
import static io.github.romanhaendler.rbed.Conditions.count;
import static io.github.romanhaendler.rbed.Conditions.during;
import static io.github.romanhaendler.rbed.Conditions.latest;
import static io.github.romanhaendler.rbed.Conditions.totalDuration;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.Differential.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Twin runs for the Event state other Rules read: a Snapshot at every moment of a hand-written
 * input restores into a Detector that never differs from the original.
 */
class EventStateSnapshotTest {
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");
  static final Channel Z = Channel.named("z");

  /** Runs of x above 5 at 10..30, 60..80, 100..110, 150..170; y and z vary on their own. */
  static List<Op> ops() {
    long[] t = {0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190};
    double[] x = {0, 9, 8, 7, 1, 0, 9, 3, 7, 1, 9, 6, 1, 0, 0, 8, 12, 2, 0, 0};
    double[] y = {0, 2, 0, 3, 3, 2, 0, 4, 4, 0, 5, 0, 2, 3, 0, 0, 6, 6, 3, 0};
    double[] z = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
    List<Op> ops = new ArrayList<>();
    for (int i = 0; i < t.length; i++) ops.add(Op.push(t[i], "x", x[i], "y", y[i], "z", z[i]));
    ops.add(Op.advance(300));
    ops.add(Op.close());
    return ops;
  }

  static Event ended(String name, long start, long confirmed, long end) {
    return new Event(name, start, OptionalLong.of(confirmed), OptionalLong.of(end), false, false,
        Map.of());
  }

  private static void twins(Consumer<Detector.Builder> config, Consumer<Detector.Builder> restore) {
    Map<String, Integer> stats = new TreeMap<>();
    List<Op> ops = ops();
    for (int k = 0; k <= ops.size(); k++) {
      Result r = Differential.compareRestoredAt(config, restore, List.of(), ops, k);
      if (r.difference() != null) {
        throw new AssertionError("Snapshot after " + k + " ops\n" + r.difference().report());
      }
      r.stats().forEach((n, v) -> stats.merge(n, v, Integer::sum));
    }
    assertTrue(stats.getOrDefault("confirmed", 0) > 0, "vacuous: " + stats);
  }

  private static void twins(Consumer<Detector.Builder> config) {
    twins(config, config);
  }

  private static final Rule RUN = Rule.named("Run").when(X.above(5)).endWhen(X.below(2));

  @Test
  void duringAnEvent() {
    twins(b -> b.rules(RUN, Rule.named("Hot").when(Y.above(1).and(during(RUN)))));
  }

  @Test
  void afterWithinWindowOpenAndClosed() {
    twins(b -> b.rules(RUN, Rule.named("Follow").when(after(RUN).within(25).and(Y.above(1)))));
  }

  @Test
  void afterUnless() {
    Rule other = Rule.named("Other").when(Y.above(4));
    twins(b -> b.rules(RUN, other, Rule.named("Quiet").when(after(RUN).unless(other))));
  }

  @Test
  void afterWithinUnlessInAnEndCondition() {
    Rule other = Rule.named("Other").when(Y.above(4));
    twins(b -> b.rules(RUN, other, Rule.named("W").when(Y.above(0))
        .endWhen(after(RUN).unless(other).within(30))));
  }

  @Test
  void countWithinNearItsLimitAndWithTimestampsLeavingTheWindow() {
    Rule hits = Rule.named("Hits").when(Y.above(1));
    for (int window : new int[] {20, 40, 75, 200}) {
      twins(b -> b.rules(hits, Rule.named("Many").when(count(hits).within(window).above(1))));
    }
  }

  @Test
  void countWithoutAWindow() {
    twins(b -> b.rules(RUN, Rule.named("Third").when(count(RUN).above(2))));
  }

  @Test
  void totalDurationOfEvents() {
    twins(b -> b.rules(RUN, Rule.named("Long").when(totalDuration(RUN).above(35))));
  }

  @Test
  void latestDurationAndAggregates() {
    twins(b -> b.rules(RUN,
        Rule.named("D").when(latest(RUN).duration().above(15)),
        Rule.named("Mx").when(Y.above(latest(RUN).max(X).times(0.2))),
        Rule.named("Mn").when(Y.below(latest(RUN).min(X).plus(3))),
        Rule.named("Me").when(Y.above(latest(RUN).mean(X).times(0.3)))));
  }

  /** z is aggregated by Run only because another Rule reads it with latest. */
  @Test
  void latestOfAChannelTheReferencedRuleAggregatesOnlyBecauseOfLatest() {
    twins(b -> b.rules(RUN, Rule.named("ZHigh").when(Z.above(latest(RUN).mean(Z)))));
    twins(b -> b.rules(RUN, Rule.named("ZLow").when(Z.below(latest(RUN).max(Z).plus(0)).and(Y.above(1)))));
  }

  /** A self-reference is rejected at build (SequenceTest), so the nearest case is a chain of readers. */
  @Test
  void aChainOfRulesReadingEachOther() {
    Rule hot = Rule.named("Hot").when(Y.above(1).and(during(RUN)));
    twins(b -> b.rules(RUN, hot,
        Rule.named("Chain").when(after(hot).within(30).and(Z.above(latest(hot).mean(Z)))),
        Rule.named("Tot").when(totalDuration(hot).above(20).and(count(hot).above(1)))));
  }

  @Test
  void historySuppliedAtTheFirstBuild() {
    List<Event> history = List.of(ended("Run", -60, -50, -30), ended("Run", -20, -10, -5));
    Consumer<Detector.Builder> without = b -> b.rules(RUN,
        Rule.named("Many").when(count(RUN).within(60).above(2)),
        Rule.named("Total").when(totalDuration(RUN).above(30)),
        Rule.named("Follow").when(after(RUN).within(40).and(Y.above(1))));
    twins(b -> without.accept(b.history(history)), without);
  }
}
