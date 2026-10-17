package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.Differential.Result;
import io.github.romanhaendler.rbed.Differential.Side;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The differential harness and its generator. Run more cases with fresh seeds with
 * {@code mvn test -Pdifferential}; reproduce one failure with
 * {@code mvn test -Dtest=DifferentialTest -Drbed.diff.seed=<seed>}. A failure prints the shrunk
 * case as a JUnit method: paste it into this class to keep it as a regression test.
 */
class DifferentialTest {
  private static final int CASES = Integer.getInteger("rbed.diff.cases", 3000);
  private static final boolean FRESH_SEEDS = Boolean.getBoolean("rbed.diff.fresh");
  private static final Channel A = Channel.named("a");

  @Test
  void twoFreshDetectorsNeverDifferOverGeneratedCases() {
    String one = System.getProperty("rbed.diff.seed");
    long first = one != null ? Long.parseLong(one) : FRESH_SEEDS ? System.nanoTime() : 1;
    int cases = one != null ? 1 : CASES;
    if (FRESH_SEEDS) System.out.println("differential: seeds " + first + ".." + (first + cases - 1));
    int built = 0;
    Map<String, Integer> seen = new java.util.TreeMap<>();
    for (long seed = first; seed < first + cases; seed++) {
      Scenario s = ScenarioGenerator.generate(seed);
      Result r = Differential.run(s, Differential.FRESH, Differential.FRESH);
      if (r.built()) built++;
      r.stats().forEach((k, v) -> seen.merge(k, v, Integer::sum));
      Differential.assertNoDifference(s, Differential.FRESH, Differential.FRESH);
    }
    if (one == null && cases >= 1000) { // the comparison is not vacuous
      for (String what : List.of("pending", "confirmed", "ended", "discarded", "truncatedStart",
          "truncatedEnd", "exception", "warning", "watchedValue")) {
        assertTrue(seen.getOrDefault(what, 0) >= cases / 20, what + " is rare: " + seen);
      }
    }
    System.out.println("differential: " + cases + " cases, " + built + " built, " + seen);
    assertTrue(built >= cases * 0.9, "only " + built + " of " + cases + " scenarios could be built");
  }

  @Test
  void generatedCasesCoverEveryConstructAndKindOfInput() {
    Set<String> ops = new HashSet<>();
    Set<String> kinds = new HashSet<>();
    int sampling = 0, history = 0, smallLimit = 0, endConditions = 0, maxGaps = 0, aggregates = 0;
    int pulses = 0, durations = 0, samples = 0, watched = 0, atThreshold = 0;
    for (long seed = 1; seed <= Math.min(CASES, 3000); seed++) {
      Scenario s = ScenarioGenerator.generate(seed);
      if (s.samplingRate() > 0) sampling++;
      if (!s.history().isEmpty()) history++;
      if (s.retentionLimit() > 0) smallLimit++;
      if (!s.watched().isEmpty()) watched++;
      for (Scenario.RuleSpec r : s.rules()) {
        if (r.endWhen() != null) endConditions++;
        if (r.maxGap() >= 0) maxGaps++;
        if (!r.aggregate().isEmpty()) aggregates++;
        if (r.forDuration() >= 0 || r.endDuration() >= 0) durations++;
        if (r.forSamples() > 0) samples++;
        collect(r.when(), ops);
        if (r.endWhen() != null) collect(r.endWhen(), ops);
      }
      s.watched().forEach(w -> collect(w, ops));
      Set<Double> constants = new HashSet<>(s.constants());
      long last = Long.MIN_VALUE;
      for (Op op : s.ops()) {
        kinds.add(op.kind().name());
        if (op.kind() == Op.Kind.PUSH) {
          if (op.timestamp() <= last) kinds.add("NON_INCREASING");
          last = Math.max(last, op.timestamp());
        }
        for (double v : op.values().values()) {
          if (Double.isNaN(v)) kinds.add("NAN");
          if (Double.isInfinite(v)) kinds.add("INFINITE");
          if (constants.contains(v)) atThreshold++;
          if (Double.compare(v, -0.0) == 0) kinds.add("NEGATIVE_ZERO");
        }
        if (op.values().containsKey("p")) pulses++;
      }
    }
    for (String op : List.of("ch", "pulse", "avgS", "avgD", "rate", "age", "rising", "falling", "abs",
        "plus", "minus", "times", "div", "plusC", "minusC", "timesC", "divC", "above", "below",
        "aboveC", "belowC", "between", "and", "or", "not", "during", "after", "count", "total",
        "latestMin", "latestMax", "latestMean", "latestDur")) {
      assertTrue(ops.contains(op), "never generated: " + op);
    }
    for (String kind : List.of("PUSH", "PUSH_VALUES", "ADVANCE", "CLOSE", "NON_INCREASING", "NAN",
        "INFINITE", "NEGATIVE_ZERO")) {
      assertTrue(kinds.contains(kind), "never generated input: " + kind);
    }
    for (int n : new int[] {sampling, history, smallLimit, endConditions, maxGaps, aggregates,
        pulses, durations, samples, watched, atThreshold}) {
      assertTrue(n > 0);
    }
  }

  private static void collect(Scenario.Node n, Set<String> into) {
    into.add(n.op());
    n.kids().forEach(k -> collect(k, into));
  }

  @Test
  void aDroppedWindowAndItsWarningAreComparedToo() {
    Scenario.Built b = new Scenario(0, 0, 2, List.of(new Scenario.RuleSpec("R",
        Scenario.Node.ofA("aboveC", 0, Scenario.Node.ofA("avgD", 1000, Scenario.Node.named("ch", "a", 0))),
        0, -1, null, 0, -1, -1, List.of())), List.of(), List.of(), List.of()).build();
    Result r = Differential.compare(b.config(), b.watched(),
        List.of(Op.push(1, "a", 1.0), Op.push(2, "a", 1.0), Op.push(3, "a", 1.0)),
        Differential.FRESH, Differential.FRESH);
    assertNull(r.difference());
    assertEquals(3, r.moments());
  }

  @Test
  void aDeliberatelyDifferentRuleIsReportedWithItsMoment() {
    Rule hot = Rule.named("Hot").when(A.above(5));
    Side other = (config, l) -> Detector.builder().listener(l)
        .rules(Rule.named("Hot").when(A.above(6))).build();
    Result r = Differential.compare(b -> b.rules(hot), List.of(),
        List.of(Op.push(0, "a", 1.0), Op.push(10, "a", 5.5), Op.push(20, "a", 9.0)),
        Differential.FRESH, other);
    assertNotNull(r.difference());
    assertEquals(2, r.difference().moment());
    assertEquals("callback count", r.difference().field());
    assertTrue(r.difference().report().contains("push(10, {a=5.5})"), r.difference().report());
    assertEquals("callback count: 2", r.difference().a()); // pending and confirmed
    assertEquals("callback count: 0", r.difference().b());
    assertTrue(r.difference().report().contains("callback 0: pending Hot start=10"));
  }

  @Test
  void anExtraRuleInOneDetectorIsReportedForGeneratedCases() {
    Side extra = (config, l) -> {
      Detector.Builder b = Detector.builder().listener(l);
      config.accept(b);
      return b.rules(Rule.named("Extra").when(A.above(-1e308).or(A.below(1e308)))).build();
    };
    int reported = 0, cases = 300;
    for (long seed = 1; seed <= cases; seed++) {
      Scenario s = ScenarioGenerator.generate(seed);
      Result r = Differential.run(s, Differential.FRESH, extra);
      if (r.difference() != null) reported++;
    }
    assertTrue(reported >= cases * 0.7, "reported " + reported + " of " + cases);
  }

  @Test
  void aDifferenceInTheExceptionOfAnInputIsReported() {
    Rule hot = Rule.named("Hot").when(A.above(5));
    Side sampled = (config, l) -> Detector.builder().listener(l).samplingRate(1).rules(hot).build();
    Result res = Differential.compare(b -> b.rules(hot), List.of(),
        List.of(Op.push(0, "a", 1.0)), Differential.FRESH, sampled);
    assertNotNull(res.difference());
    assertEquals("outcome", res.difference().field());
    assertEquals("outcome: ok", res.difference().a());
    assertTrue(res.difference().b().startsWith("outcome: java.lang.IllegalStateException: "));
  }

  @Test
  void theSameExceptionOnBothSidesIsNoDifference() {
    Rule hot = Rule.named("Hot").when(A.above(5));
    Result res = Differential.compare(b -> b.rules(hot), List.of(),
        List.of(Op.push(5, "a", 1.0), Op.push(5, "a", 1.0), Op.push(6, "a", Double.NaN),
            Op.close(), Op.push(9, "a", 1.0), Op.advance(10)),
        Differential.FRESH, Differential.FRESH);
    assertNull(res.difference());
  }

  @Test
  void doublesAreComparedBitForBit() {
    Rule r = Rule.named("R").when(A.below(10)).aggregate(A);
    // B reports a -0.0 where A reports 0.0; they are equal as numbers
    Side negative = (config, l) -> FRESH_WITH(config, new EventListener() {
      @Override public void onEnded(Event e) {
        Aggregates a = e.aggregates(A);
        Aggregates flipped = new Aggregates(a.min() == 0 ? -0.0 : a.min(), a.max(), a.mean(),
            a.count(), a.timeWeightedMean(), a.integral());
        l.onEnded(new Event(e.name(), e.start(), e.confirmed(), e.end(), e.truncatedStart(),
            e.truncatedEnd(), Map.of(A, flipped)));
      }
      @Override public void onPending(Event e) { l.onPending(e); }
      @Override public void onConfirmed(Event e) { l.onConfirmed(e); }
      @Override public void onDiscarded(Event e) { l.onDiscarded(e); }
    });
    Result res = Differential.compare(b -> b.rules(r), List.of(),
        List.of(Op.push(0, "a", 0.0), Op.push(10, "a", 20.0)), Differential.FRESH, negative);
    assertNotNull(res.difference());
    assertTrue(res.difference().a().contains("min=0.0/0 "), res.difference().a());
    assertTrue(res.difference().b().contains("min=-0.0/8000000000000000 "), res.difference().b());
  }

  private static Detector FRESH_WITH(java.util.function.Consumer<Detector.Builder> config,
      EventListener l) {
    return Differential.FRESH.build(config, l);
  }

  @Test
  void aFailurePrintsSeedShrunkInputAndJUnitSource() {
    // B has an extra Rule, so every case that pushes anything differs
    Side extra = (config, l) -> {
      Detector.Builder b = Detector.builder().listener(l);
      config.accept(b);
      return b.rules(Rule.named("Extra").when(A.above(-1e308).or(A.below(1e308)))).build();
    };
    Scenario failing = null;
    for (long seed = 1; failing == null; seed++) {
      Scenario c = ScenarioGenerator.generate(seed);
      if (Differential.run(c, Differential.FRESH, extra).difference() != null) failing = c;
    }
    final Scenario f = failing;
    AssertionError e = org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class,
        () -> Differential.assertNoDifference(f, Differential.FRESH, extra));
    String message = e.getMessage();
    assertTrue(message.contains("Seed " + f.seed()), message);
    assertTrue(message.contains("@Test\n  void regression_seed_" + f.seed()), message);
    assertTrue(message.contains("Differential.assertFresh("), message);
    Scenario small = Differential.shrink(f, c -> Differential.run(c, Differential.FRESH, extra).difference() != null);
    assertTrue(small.ops().size() <= 1, "shrunk to " + small.ops());
    assertTrue(small.rules().size() <= f.rules().size());
  }

  @Test
  void aShrunkCaseAsPrintedRunsAsAPlainTest() {
    // what the printed regression test looks like for a small case
    Rule r0 = Rule.named("R0").when(Channel.named("a").above(1.0)).forSamples(2)
        .endWhen(Channel.named("a").below(0.0)).maxGap(50L).aggregate(Channel.named("a"));
    Expression w0 = Conditions.count(r0).within(100L);
    OptionalLong ignored = OptionalLong.empty();
    assertTrue(ignored.isEmpty());
    Differential.assertFresh(b -> b.rules(r0).watch(w0).retentionLimit(3),
        List.of(w0),
        Op.push(0L, "a", 2.0),
        Op.push(10L, "a", 2.0),
        Op.advance(500L),
        Op.push(600L, "a", -1.0),
        Op.close(),
        Op.push(700L, "a", 1.0));
  }

  @Test
  void scenarioSourceRoundTripsThroughTheLibrary() {
    Map<String, Double> m = new LinkedHashMap<>();
    m.put("a", -0.0);
    assertEquals("Op.push(5L, \"a\", -0.0)", new Op(Op.Kind.PUSH, 5, m).java());
  }
}
