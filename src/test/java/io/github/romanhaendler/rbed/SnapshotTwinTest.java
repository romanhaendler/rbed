package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.Differential.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The guarantee, by twin runs: a Snapshot taken at every moment of an input (before the first
 * Sample, after every Sample, advance and close) restores into a Detector that never differs
 * from the original.
 */
class SnapshotTwinTest {
  private static final int CASES = Integer.getInteger("rbed.twin.cases", 400);
  private static final Channel A = Channel.named("a");
  private static final Channel B = Channel.named("b");

  /** The tracer: Channel comparisons, Rules without durations. */
  @Test
  void aSnapshotAtEveryMomentOfTheTracerRuleSetRestoresATwinThatNeverDiffers() {
    Rule up = Rule.named("Up").when(A.above(10));
    Rule both = Rule.named("Both").when(A.above(5).and(B.below(3)));
    List<Op> ops = List.of(
        Op.push(0, "a", 0, "b", 0),
        Op.push(10, "a", 12, "b", 1),
        Op.push(20, "a", 14),
        Op.push(30, "a", 4, "b", 5),
        Op.push(40, "a", 6, "b", 2),
        Op.push(50, "a", 20, "b", 2),
        Op.push(60, "a", 1),
        Op.advance(70),
        Op.push(80, "a", 30, "b", 0),
        Op.close(),
        Op.push(90, "a", 1));
    Result total = everyMoment(b -> b.rules(up, both), b -> b.rules(up, both), List.of(), ops);
    assertTrue(total.stats().getOrDefault("ended", 0) > 0, "vacuous: " + total.stats());
  }

  private static Result everyMoment(java.util.function.Consumer<Detector.Builder> config,
      java.util.function.Consumer<Detector.Builder> restoreConfig,
      List<io.github.romanhaendler.rbed.Expression> watched, List<Op> ops) {
    Map<String, Integer> stats = new TreeMap<>();
    for (int k = 0; k <= ops.size(); k++) {
      Result r = Differential.compareRestoredAt(config, restoreConfig, watched, ops, k);
      if (r.difference() != null) {
        throw new AssertionError("Snapshot after " + k + " ops\n" + r.difference().report());
      }
      r.stats().forEach((name, n) -> stats.merge(name, n, Integer::sum));
    }
    return new Result(null, true, ops.size(), stats);
  }

  @Test
  void aSnapshotAtEveryMomentOfGeneratedCasesRestoresATwinThatNeverDiffers() {
    String one = System.getProperty("rbed.diff.seed");
    long first = one != null ? Long.parseLong(one) : 1;
    int cases = one != null ? 1 : CASES;
    Map<String, Integer> seen = new TreeMap<>();
    int built = 0;
    for (long seed = first; seed < first + cases; seed++) {
      Scenario s = ScenarioGenerator.generate(seed);
      Scenario.Built b;
      try {
        b = s.build();
      } catch (RuntimeException e) {
        continue;
      }
      List<Op> ops = new ArrayList<>(s.ops());
      // after close, whatever follows must be rejected the same way
      ops.add(Op.close());
      ops.add(s.samplingRate() > 0 ? Op.pushValues("a", 1.0) : Op.push(Long.MAX_VALUE / 2, "a", 1.0));
      if (!buildable(b)) continue;
      built++;
      for (int k = 0; k <= ops.size(); k++) {
        Result r = Differential.compareRestoredAt(
            b.config(), b.configWithoutHistory(), b.watched(), ops, k);
        if (r.difference() != null) {
          throw new AssertionError("Seed " + seed + " (rerun with -Drbed.diff.seed=" + seed
              + "), Snapshot after " + k + " ops\n" + r.difference().report()
              + "\nScenario: " + s.toJava("seed_" + seed));
        }
        r.stats().forEach((name, n) -> seen.merge(name, n, Integer::sum));
      }
    }
    if (one == null) {
      assertTrue(built >= cases * 0.9, "only " + built + " of " + cases + " built");
      for (String what : List.of("pending", "confirmed", "ended", "discarded", "truncatedStart",
          "truncatedEnd", "exception", "warning", "watchedValue")) {
        assertTrue(seen.getOrDefault(what, 0) > 0, what + " never happened: " + seen);
      }
    }
    System.out.println("snapshot twins: " + cases + " cases, " + built + " built, " + seen);
  }

  /** Whether the full configuration builds (a scenario whose build is rejected has no twin). */
  private static boolean buildable(Scenario.Built b) {
    try {
      Detector.Builder builder = Detector.builder();
      b.config().accept(builder);
      builder.build();
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }
}
