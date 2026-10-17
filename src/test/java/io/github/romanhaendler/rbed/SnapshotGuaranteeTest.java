package io.github.romanhaendler.rbed;

import static io.github.romanhaendler.rbed.Conditions.count;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Differential.Mode;
import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.Differential.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The restore guarantee at scale: over thousands of generated cases (fixed seeds; {@code mvn test
 * -Pdifferential} runs far more with fresh seeds, {@code -Drbed.diff.seed=S} reruns one), a
 * restored Detector never differs from the original, and every Snapshot of a restored Detector
 * equals the one it came from. Three ways: a twin restored at every moment and run to the end, a
 * Detector that only takes Snapshots at every moment or at random moments, and a Detector replaced
 * by a restore of its own Snapshot after every single moment (a chain) or at random moments.
 */
class SnapshotGuaranteeTest {
  private static final int CASES = Integer.getInteger("rbed.diff.cases", 3000);
  private static final boolean FRESH_SEEDS = Boolean.getBoolean("rbed.diff.fresh");

  private interface Check {
    Result run(Scenario.Built b, List<Op> ops, long seed);
  }

  @Test
  void aTwinRestoredAtEveryMomentNeverDiffers() {
    Map<String, Integer> seen = forEveryCase((b, ops, seed) -> {
      Map<String, Integer> stats = new TreeMap<>();
      for (int k = 0; k <= ops.size(); k++) {
        Result r = Differential.compareRestoredAt(
            b.config(), b.configWithoutHistory(), b.watched(), ops, k);
        if (r.difference() != null) {
          throw new AssertionError("Snapshot after " + k + " ops\n" + r.difference().report());
        }
        r.stats().forEach((n, v) -> stats.merge(n, v, Integer::sum));
      }
      stats.merge("snapshot", ops.size() + 1, Integer::sum);
      return new Result(null, true, ops.size(), stats);
    });
    assertNotVacuous(seen);
  }

  @Test
  void takingSnapshotsAtEveryMomentOrAtRandomMomentsChangesNothing() {
    Map<String, Integer> seen = forEveryCase((b, ops, seed) -> {
      Result all = Differential.compareWithSnapshots(b.config(), b.configWithoutHistory(),
          b.watched(), ops, Mode.SNAPSHOT_ONLY, null, 1);
      if (all.difference() != null) return all;
      return Differential.compareWithSnapshots(b.config(), b.configWithoutHistory(),
          b.watched(), ops, Mode.SNAPSHOT_ONLY, new SplittableRandom(seed), 0.3);
    });
    assertNotVacuous(seen);
  }

  @Test
  void aChainOfRestoresAfterEverySingleMomentNeverDiffers() {
    Map<String, Integer> seen = forEveryCase((b, ops, seed) ->
        Differential.compareWithSnapshots(b.config(), b.configWithoutHistory(), b.watched(), ops,
            Mode.RESTORE, null, 1));
    assertNotVacuous(seen);
  }

  @Test
  void restoresAtRandomMomentsNeverDiffer() {
    Map<String, Integer> seen = forEveryCase((b, ops, seed) ->
        Differential.compareWithSnapshots(b.config(), b.configWithoutHistory(), b.watched(), ops,
            Mode.RESTORE, new SplittableRandom(seed), 0.25));
    assertNotVacuous(seen);
  }

  /**
   * Windows that reach the Retention Limit (a moving average over a very long duration, the
   * timestamps behind count(...)) keep a Snapshot at a size set by the limit, not by the length of
   * the stream; a Snapshot with a limit of 100 is never larger than that.
   */
  @Test
  void aSnapshotIsBoundedByTheRetentionLimitHoweverLongTheStream() {
    Channel a = Channel.named("a");
    Rule hit = Rule.named("Hit").when(a.above(5)).endWhen(a.below(1));
    java.util.function.Supplier<Detector> fresh = () -> Detector.builder().retentionLimit(100)
        .rules(hit,
            Rule.named("Slow").when(a.averageOverDuration(86_400_000L).above(5)),
            Rule.named("Many").when(count(hit).within(86_400_000L).above(1_000_000)))
        .build();
    Detector d = fresh.get();
    int atLimit = 0;
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger("io.github.romanhaendler.rbed");
    java.util.logging.Level level = logger.getLevel();
    logger.setLevel(java.util.logging.Level.OFF); // the limit is reached on purpose
    try {
      for (int i = 0; i < 20_000; i++) {
        d.push(i * 10L, Map.of(a, i % 4 < 2 ? 9.0 : 0.0));
        if (i == 1_000) atLimit = d.snapshot().length;
      }
    } finally {
      logger.setLevel(level);
    }
    int later = d.snapshot().length;
    assertTrue(atLimit > 100 * 16, "windows should be full by now: " + atLimit);
    // two windows of at most 100 entries of 16 bytes (time and value; the timestamps of count are
    // 8), plus a few hundred bytes of fixed state
    assertTrue(later <= atLimit + 8, "grew from " + atLimit + " to " + later + " bytes");
    assertTrue(later < 2 * 100 * 16 + 1_000, later + " bytes for a limit of 100");
    assertTrue(d.retainedValues() <= 2 * 100 + 10,"retained " + d.retainedValues());
    Detector twin = Detector.builder().retentionLimit(100)
        .rules(hit,
            Rule.named("Slow").when(a.averageOverDuration(86_400_000L).above(5)),
            Rule.named("Many").when(count(hit).within(86_400_000L).above(1_000_000)))
        .restore(d.snapshot()).build();
    assertTrue(java.util.Arrays.equals(d.snapshot(), twin.snapshot()));
  }

  private static void assertNotVacuous(Map<String, Integer> seen) {
    if (System.getProperty("rbed.diff.seed") != null) return;
    for (String what : List.of("pending", "confirmed", "ended", "discarded", "truncatedStart",
        "truncatedEnd", "exception", "warning", "watchedValue", "snapshot")) {
      assertTrue(seen.getOrDefault(what, 0) > 0, what + " never happened: " + seen);
    }
  }

  /** Runs the check over the generated cases; the ops end with a close and a rejected push. */
  private static Map<String, Integer> forEveryCase(Check check) {
    String one = System.getProperty("rbed.diff.seed");
    long first = one != null ? Long.parseLong(one) : FRESH_SEEDS ? System.nanoTime() : 1;
    int cases = one != null ? 1 : CASES;
    if (FRESH_SEEDS) System.out.println("guarantee: seeds " + first + ".." + (first + cases - 1));
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
      if (!buildable(b)) continue;
      List<Op> ops = new ArrayList<>(s.ops());
      ops.add(Op.close());
      ops.add(s.samplingRate() > 0 ? Op.pushValues("a", 1.0) : Op.push(Long.MAX_VALUE / 2, "a", 1.0));
      built++;
      Result r;
      try {
        r = check.run(b, ops, seed);
      } catch (AssertionError e) {
        throw new AssertionError("Seed " + seed + " (rerun with -Drbed.diff.seed=" + seed + ")\n"
            + e.getMessage() + "\nScenario: " + s.toJava("seed_" + seed));
      }
      if (r.difference() != null) {
        throw new AssertionError("Seed " + seed + " (rerun with -Drbed.diff.seed=" + seed + ")\n"
            + r.difference().report() + "\nScenario: " + s.toJava("seed_" + seed));
      }
      r.stats().forEach((n, v) -> seen.merge(n, v, Integer::sum));
    }
    if (one == null) assertTrue(built >= cases * 0.9, "only " + built + " of " + cases + " built");
    System.out.println("guarantee: " + cases + " cases, " + built + " built, " + seen);
    return seen;
  }

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
