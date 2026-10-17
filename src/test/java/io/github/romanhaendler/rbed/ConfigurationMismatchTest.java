package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Differential.Difference;
import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.Differential.Run;
import io.github.romanhaendler.rbed.Scenario.Construct;
import io.github.romanhaendler.rbed.Scenario.Node;
import io.github.romanhaendler.rbed.Scenario.RuleSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Restoring a Snapshot into a Detector whose configuration differs in one way (any parameter of
 * any construct, a name, the order of Rules, aggregated Channels or watched Expressions, a Channel
 * kind, an option) must either be rejected by {@code build()}, or, only if the change is
 * semantically void, behave exactly like the restore into the original configuration. Works on
 * generated Scenarios, so every construct is covered. {@code -Drbed.diff.seed=S} reruns one seed.
 */
class ConfigurationMismatchTest {
  private static final int CASES = Math.max(1, Integer.getInteger("rbed.diff.cases", 3000) / 3);
  private static final int MUTATIONS_PER_CASE = 12;
  private static final boolean FRESH_SEEDS = Boolean.getBoolean("rbed.diff.fresh");

  private static final Map<Construct, Construct> SWAPS = Map.ofEntries(
      Map.entry(Construct.ABOVE_C, Construct.BELOW_C), Map.entry(Construct.BELOW_C, Construct.ABOVE_C),
      Map.entry(Construct.ABOVE, Construct.BELOW), Map.entry(Construct.BELOW, Construct.ABOVE),
      Map.entry(Construct.RISING, Construct.FALLING), Map.entry(Construct.FALLING, Construct.RISING),
      Map.entry(Construct.AND, Construct.OR), Map.entry(Construct.OR, Construct.AND),
      Map.entry(Construct.AVG_S, Construct.AVG_D), Map.entry(Construct.AVG_D, Construct.AVG_S),
      Map.entry(Construct.PLUS, Construct.MINUS), Map.entry(Construct.MINUS, Construct.PLUS),
      Map.entry(Construct.TIMES, Construct.DIV), Map.entry(Construct.DIV, Construct.TIMES),
      Map.entry(Construct.PLUS_C, Construct.MINUS_C), Map.entry(Construct.MINUS_C, Construct.PLUS_C),
      Map.entry(Construct.TIMES_C, Construct.DIV_C), Map.entry(Construct.DIV_C, Construct.TIMES_C),
      Map.entry(Construct.LATEST_MIN, Construct.LATEST_MAX), Map.entry(Construct.LATEST_MAX, Construct.LATEST_MEAN),
      Map.entry(Construct.LATEST_MEAN, Construct.LATEST_MIN), Map.entry(Construct.RATE, Construct.AGE), Map.entry(Construct.AGE, Construct.RATE),
      Map.entry(Construct.DURING, Construct.AFTER), Map.entry(Construct.AFTER, Construct.DURING),
      Map.entry(Construct.COUNT, Construct.TOTAL), Map.entry(Construct.TOTAL, Construct.COUNT));
  private static final Set<Construct> CONDITIONS = Set.of(Construct.ABOVE_C, Construct.BELOW_C, Construct.ABOVE,
      Construct.BELOW, Construct.BETWEEN, Construct.RISING, Construct.FALLING, Construct.AND,
      Construct.OR, Construct.NOT, Construct.DURING, Construct.AFTER);

  /** Names that spell other parts of a configuration, or that break naive encodings. */
  static final List<String> ADVERSARIAL = List.of("a.abs()", "a.above(1.0)", "a b", "a,b", "a, b",
      "a\")", "\"", "(", ")", ".", "pulse channel a", "channel a", "a pulse", "pulse", "\u00e9",
      "\u0430", "\n", "a.rate()", "during(R0)", "R0).and(during(R1", "x\\", "5:abcde", "1:a");

  /** The readable text of a tree, built against placeholder Rules (an Expression names a Rule by name). */
  static String text(Node n, List<String> ruleNames) {
    Map<String, Rule> known = new java.util.HashMap<>();
    for (String r : ruleNames) {
      known.put(r, Rule.named(r).when(Channel.named("a").above(0)));
    }
    try {
      return n.build(known).toString();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static Node with(Node n, double a, double b, List<Node> kids, List<String> names) {
    return new Node(n.op(), n.name(), a, b, kids, names);
  }

  /** All single-step variants of a tree. */
  static List<Node> variants(Node n, List<String> ruleNames) {
    List<Node> out = new ArrayList<>();
    Construct op = n.op();
    boolean hasA = op.constant || op == Construct.BETWEEN || op == Construct.AVG_S
        || op == Construct.AVG_D || ((op == Construct.AFTER || op == Construct.COUNT) && n.a() >= 0);
    if (hasA) {
      out.add(with(n, n.a() + 1, n.b(), n.kids(), n.names()));
      out.add(with(n, n.a() - 1, n.b(), n.kids(), n.names()));
      out.add(with(n, Math.nextUp(n.a()), n.b(), n.kids(), n.names()));
      out.add(with(n, -n.a(), n.b(), n.kids(), n.names()));
    }
    if (op == Construct.AFTER || op == Construct.COUNT) { // toggle the window
      out.add(with(n, n.a() >= 0 ? -1 : 7, n.b(), n.kids(), n.names()));
    }
    if (op == Construct.BETWEEN) {
      out.add(with(n, n.a(), n.b() + 1, n.kids(), n.names()));
      out.add(with(n, n.a(), Math.nextDown(n.b()), n.kids(), n.names()));
    }
    Construct swapped = SWAPS.get(op);
    if (swapped != null) {
      out.add(new Node(swapped, n.name(), n.a(), n.b(), n.kids(), n.names()));
    }
    if (op == Construct.CH || op == Construct.PULSE) {
      out.add(new Node(op == Construct.CH ? Construct.PULSE : Construct.CH, n.name(), 0, 0, n.kids(), n.names()));
      List<String> others = new ArrayList<>(List.of("a", "b", "c", "p"));
      others.addAll(ADVERSARIAL);
      for (String other : others) {
        if (!other.equals(n.name())) out.add(new Node(op, other, 0, 0, n.kids(), n.names()));
      }
    } else if (!CONDITIONS.contains(op)) { // a Channel named like this Expression reads
      String spelled = text(n, ruleNames);
      if (spelled != null) {
        out.add(Node.named(Construct.CH, spelled, 0));
        out.add(Node.named(Construct.PULSE, spelled, 0));
      }
    }
    if (n.name() != null && ruleNames.contains(n.name())) {
      for (String other : ruleNames) {
        if (!other.equals(n.name())) {
          out.add(new Node(op, other, n.a(), n.b(), n.kids(), n.names()));
        }
      }
    }
    if (op.isLatest() && op != Construct.LATEST_DUR) {
      for (String c : List.of("a", "b", "c")) {
        if (!c.equals(n.names().get(0))) out.add(with(n, n.a(), n.b(), n.kids(), List.of(c)));
      }
    }
    if (op == Construct.AFTER && !ruleNames.isEmpty()) {
      List<String> names = n.names();
      if (names.size() > 1) {
        List<String> reversed = new ArrayList<>(names);
        Collections.reverse(reversed);
        out.add(with(n, n.a(), n.b(), n.kids(), reversed));
      }
      if (!names.isEmpty()) out.add(with(n, n.a(), n.b(), n.kids(), names.subList(1, names.size())));
      List<String> more = new ArrayList<>(names);
      more.add(ruleNames.get(0));
      out.add(with(n, n.a(), n.b(), n.kids(), more));
    }
    if (n.kids().size() == 2) {
      out.add(with(n, n.a(), n.b(), List.of(n.kids().get(1), n.kids().get(0)), n.names()));
    }
    if (CONDITIONS.contains(op)) out.add(Node.of(Construct.NOT, n));
    if (op == Construct.NOT && n.kids().size() == 1) out.add(n.kids().get(0));
    for (int i = 0; i < n.kids().size(); i++) {
      for (Node v : variants(n.kids().get(i), ruleNames)) {
        List<Node> kids = new ArrayList<>(n.kids());
        kids.set(i, v);
        out.add(with(n, n.a(), n.b(), kids, n.names()));
      }
    }
    return out;
  }

  private static RuleSpec spec(RuleSpec r, Node when, int forSamples, long forDuration,
      Node endWhen, int endSamples, long endDuration, long maxGap, List<String> aggregate) {
    return new RuleSpec(r.name(), when, forSamples, forDuration, endWhen, endSamples, endDuration,
        maxGap, aggregate);
  }

  /** Every scenario that differs from {@code s} in one respect. */
  static List<Scenario> mutations(Scenario s) {
    List<Scenario> out = new ArrayList<>();
    List<String> names = s.rules().stream().map(RuleSpec::name).toList();
    out.add(s.withRetention(s.retentionLimit() + 1));
    out.add(s.withRetention(0));
    out.add(new Scenario(s.seed(), s.samplingRate() == 0 ? 10 : s.samplingRate() * 2,
        s.retentionLimit(), s.rules(), s.watched(), s.history(), s.ops()));
    out.add(new Scenario(s.seed(), 0, s.retentionLimit(), s.rules(), s.watched(), s.history(),
        s.ops()));
    for (int i = 0; i < s.rules().size(); i++) {
      List<RuleSpec> rules = new ArrayList<>(s.rules());
      if (i + 1 < rules.size()) {
        Collections.swap(rules, i, i + 1);
        out.add(s.withRules(rules));
      }
      rules = new ArrayList<>(s.rules());
      rules.remove(i);
      out.add(s.withRules(rules));
      RuleSpec r = s.rules().get(i);
      List<RuleSpec> renamed = new ArrayList<>();
      for (RuleSpec x : s.rules()) renamed.add(rename(x, r.name(), r.name() + "x"));
      out.add(s.withRules(renamed));
      for (String adversarial : ADVERSARIAL) {
        List<RuleSpec> spelled = new ArrayList<>();
        for (RuleSpec x : s.rules()) spelled.add(rename(x, r.name(), adversarial));
        out.add(s.withRules(spelled));
      }
      Node w = r.when();
      Node e = r.endWhen();
      List<String> agg = r.aggregate();
      List<RuleSpec> v = new ArrayList<>();
      v.add(spec(r, w, r.forSamples() + 1, r.forDuration(), e, r.endSamples(), r.endDuration(), r.maxGap(), agg));
      if (r.forSamples() > 0) v.add(spec(r, w, 0, r.forDuration(), e, r.endSamples(), r.endDuration(), r.maxGap(), agg));
      v.add(spec(r, w, r.forSamples(), r.forDuration() + 1 + (r.forDuration() < 0 ? 1 : 0), e, r.endSamples(), r.endDuration(), r.maxGap(), agg));
      if (r.forDuration() >= 0) v.add(spec(r, w, r.forSamples(), -1, e, r.endSamples(), r.endDuration(), r.maxGap(), agg));
      if (r.forDuration() > 0) v.add(spec(r, w, r.forSamples(), r.forDuration() - 1, e, r.endSamples(), r.endDuration(), r.maxGap(), agg));
      v.add(spec(r, w, r.forSamples(), r.forDuration(), e, r.endSamples(), r.endDuration(), r.maxGap() < 0 ? 200 : r.maxGap() + 1, agg));
      if (r.maxGap() >= 0) v.add(spec(r, w, r.forSamples(), r.forDuration(), e, r.endSamples(), r.endDuration(), -1, agg));
      if (e == null) {
        v.add(spec(r, w, r.forSamples(), r.forDuration(), w, 0, -1, r.maxGap(), agg));
        v.add(spec(r, w, r.forSamples(), r.forDuration(), Node.ofA(Construct.BELOW_C, -3, Node.named(Construct.CH, "a", 0)), 0, -1, r.maxGap(), agg));
      } else {
        v.add(spec(r, w, r.forSamples(), r.forDuration(), null, 0, -1, r.maxGap(), agg));
        v.add(spec(r, e, r.forSamples(), r.forDuration(), w, r.endSamples(), r.endDuration(), r.maxGap(), agg));
        v.add(spec(r, w, r.forSamples(), r.forDuration(), e, r.endSamples() + 1, r.endDuration(), r.maxGap(), agg));
        v.add(spec(r, w, r.forSamples(), r.forDuration(), e, r.endSamples(), r.endDuration() < 0 ? 5 : r.endDuration() + 1, r.maxGap(), agg));
        // the durations of when and endWhen exchanged
        v.add(spec(r, w, r.endSamples(), r.endDuration(), e, r.forSamples(), r.forDuration(), r.maxGap(), agg));
      }
      List<String> pool = new ArrayList<>(List.of("a", "b", "c"));
      pool.addAll(ADVERSARIAL);
      for (String c : pool) {
        if (agg.contains(c)) continue;
        List<String> last = new ArrayList<>(agg);
        last.add(c);
        v.add(spec(r, w, r.forSamples(), r.forDuration(), e, r.endSamples(), r.endDuration(), r.maxGap(), last));
        List<String> first = new ArrayList<>(agg);
        first.add(0, c);
        v.add(spec(r, w, r.forSamples(), r.forDuration(), e, r.endSamples(), r.endDuration(), r.maxGap(), first));
      }
      if (!agg.isEmpty()) {
        v.add(spec(r, w, r.forSamples(), r.forDuration(), e, r.endSamples(), r.endDuration(), r.maxGap(), agg.subList(1, agg.size())));
      }
      if (agg.size() > 1) {
        List<String> rev = new ArrayList<>(agg);
        Collections.reverse(rev);
        v.add(spec(r, w, r.forSamples(), r.forDuration(), e, r.endSamples(), r.endDuration(), r.maxGap(), rev));
      }
      for (Node x : variants(w, names.subList(0, i))) {
        v.add(spec(r, x, r.forSamples(), r.forDuration(), e, r.endSamples(), r.endDuration(), r.maxGap(), agg));
      }
      if (e != null) {
        for (Node x : variants(e, names.subList(0, i))) {
          v.add(spec(r, w, r.forSamples(), r.forDuration(), x, r.endSamples(), r.endDuration(), r.maxGap(), agg));
        }
      }
      for (RuleSpec variant : v) {
        List<RuleSpec> l = new ArrayList<>(s.rules());
        l.set(i, variant);
        out.add(s.withRules(l));
      }
    }
    List<RuleSpec> more = new ArrayList<>(s.rules());
    more.add(new RuleSpec("Extra", Node.ofA(Construct.ABOVE_C, 1, Node.named(Construct.CH, "a", 0)), 0, -1, null, 0, -1, -1, List.of()));
    out.add(s.withRules(more));
    List<RuleSpec> reversed = new ArrayList<>(s.rules());
    Collections.reverse(reversed);
    out.add(s.withRules(reversed));
    List<Node> watched = s.watched();
    for (int i = 0; i < watched.size(); i++) {
      List<Node> l = new ArrayList<>(watched);
      l.remove(i);
      out.add(s.withWatched(l));
      if (i + 1 < watched.size()) {
        l = new ArrayList<>(watched);
        Collections.swap(l, i, i + 1);
        out.add(s.withWatched(l));
      }
      for (Node x : variants(watched.get(i), names)) {
        l = new ArrayList<>(watched);
        l.set(i, x);
        out.add(s.withWatched(l));
      }
    }
    List<Node> extra = new ArrayList<>(watched);
    extra.add(Node.named(Construct.CH, "a", 0));
    out.add(s.withWatched(extra));
    out.removeIf(m -> m.equals(s));
    return out;
  }

  private static RuleSpec rename(RuleSpec r, String from, String to) {
    return new RuleSpec(r.name().equals(from) ? to : r.name(), rename(r.when(), from, to),
        r.forSamples(), r.forDuration(), r.endWhen() == null ? null : rename(r.endWhen(), from, to),
        r.endSamples(), r.endDuration(), r.maxGap(), r.aggregate());
  }

  private static Node rename(Node n, String from, String to) {
    List<Node> kids = n.kids().stream().map(k -> rename(k, from, to)).toList();
    boolean channel = n.op() == Construct.CH || n.op() == Construct.PULSE;
    String name = !channel && from.equals(n.name()) ? to : n.name();
    List<String> names = n.op().isLatest() ? n.names()
        : n.names().stream().map(x -> x.equals(from) ? to : x).toList();
    return new Node(n.op(), name, n.a(), n.b(), kids, names);
  }

  @Test
  void aSnapshotRestoresOnlyIntoAConfigurationThatBehavesTheSame() {
    String one = System.getProperty("rbed.diff.seed");
    long first = one != null ? Long.parseLong(one) : FRESH_SEEDS ? System.nanoTime() : 1;
    int cases = one != null ? 1 : CASES;
    if (FRESH_SEEDS) System.out.println("mismatch: seeds " + first + ".." + (first + cases - 1));
    Map<String, Integer> stats = new TreeMap<>();
    for (long seed = first; seed < first + cases; seed++) {
      Scenario s = ScenarioGenerator.generate(seed);
      Scenario.Built original;
      try {
        original = s.build();
      } catch (RuntimeException e) {
        continue;
      }
      List<Op> ops = new ArrayList<>(s.ops());
      ops.add(Op.close());
      ops.add(s.samplingRate() > 0 ? Op.pushValues("a", 1.0) : Op.push(Long.MAX_VALUE / 2, "a", 1.0));
      int k = new SplittableRandom(seed).nextInt(ops.size() - 1);
      byte[] bytes;
      try {
        Run run = Differential.capturing(
            warnings -> new Run(Differential.FRESH, original.config(), original.watched(), warnings));
        if (run.detector == null) continue;
        for (int i = 0; i < k; i++) run.apply(ops.get(i));
        bytes = run.detector.snapshot();
      } catch (RuntimeException e) {
        continue;
      }
      List<Scenario> all = mutations(s);
      Collections.shuffle(all, new java.util.Random(seed));
      int tried = 0;
      for (Scenario m : all) {
        if (tried == MUTATIONS_PER_CASE) break;
        Scenario.Built mutated;
        try {
          mutated = m.build();
        } catch (RuntimeException e) {
          stats.merge("not buildable", 1, Integer::sum);
          continue;
        }
        tried++;
        try {
          restore(mutated, bytes, null);
        } catch (IllegalArgumentException rejected) {
          stats.merge("rejected", 1, Integer::sum);
          continue;
        }
        Scenario.Built changed = mutated;
        Difference d = Differential.capturing(warnings -> {
          Run a = new Run((c, l) -> restore(original, bytes, l), original.config(), distinct(original), warnings);
          Run b = new Run((c, l) -> restore(changed, bytes, l), changed.config(), distinct(changed), warnings);
          for (int i = k; i < ops.size(); i++) {
            Op op = ops.get(i);
            Difference x = Differential.diff(i + 1, op.toString(), a.apply(op), b.apply(op));
            if (x != null) return x;
          }
          return null;
        });
        stats.merge("accepted", 1, Integer::sum);
        if (d != null) {
          throw new AssertionError("Seed " + seed + " (rerun with -Drbed.diff.seed=" + seed
              + "): a Snapshot taken after " + k + " ops was restored into a changed configuration"
              + " and behaves differently.\nChanged to: " + m.toJava("mutated") + "\n"
              + d.report() + "\nOriginal: " + s.toJava("original"));
        }
      }
    }
    System.out.println("mismatch: " + cases + " cases, " + stats);
    if (one == null) {
      assertTrue(stats.getOrDefault("rejected", 0) > cases, "too few rejections: " + stats);
    }
  }

  /** A Detector watches an Expression once, however often the Scenario lists it. */
  private static List<Expression> distinct(Scenario.Built b) {
    return new ArrayList<>(new java.util.LinkedHashSet<>(b.watched()));
  }

  private static Detector restore(Scenario.Built b, byte[] bytes, EventListener listener) {
    Detector.Builder builder = Detector.builder();
    if (listener != null) builder.listener(listener);
    b.configWithoutHistory().accept(builder);
    return builder.restore(bytes).build();
  }

  private static void assertRejected(Rule[] original, Rule[] other, Channel... channels) {
    Detector d = Detector.builder().rules(original).build();
    byte[] bytes = d.snapshot();
    assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().rules(other).restore(bytes).build());
  }

  @Test
  void aChannelNamedLikeAnExpressionIsNotThatExpression() {
    Channel x = Channel.named("x");
    Channel spelled = Channel.named("x.abs()");
    Rule r1 = Rule.named("R1").when(spelled.above(1.0)).forSamples(2);
    Rule r2 = Rule.named("R2").when(x.abs().above(1.0)).forSamples(2);
    Rule s1 = Rule.named("R1").when(x.abs().above(1.0)).forSamples(2);
    Rule s2 = Rule.named("R2").when(spelled.above(1.0)).forSamples(2);
    assertRejected(new Rule[] {r1, r2}, new Rule[] {s1, s2});
  }

  @Test
  void aRuleNamedLikeTwoRulesInUnlessIsNotThoseRules() {
    Rule a = Rule.named("A").when(Channel.named("x").above(1.0));
    Rule b = Rule.named("B").when(Channel.named("x").above(2.0));
    Rule both = Rule.named("A, B").when(Channel.named("x").above(3.0));
    Rule p = Rule.named("P").when(Channel.named("x").above(4.0));
    Rule two = Rule.named("T").when(Conditions.after(p).unless(a, b));
    Rule one = Rule.named("T").when(Conditions.after(p).unless(both));
    assertRejected(new Rule[] {a, b, both, p, two}, new Rule[] {a, b, both, p, one});
  }

  @Test
  void anAggregatedChannelNamedLikeAListIsNotThatList() {
    Rule two = Rule.named("R").when(Channel.named("x").above(1.0))
        .aggregate(Channel.named("a"), Channel.named("b"));
    Rule one = Rule.named("R").when(Channel.named("x").above(1.0))
        .aggregate(Channel.named("a, b"));
    assertRejected(new Rule[] {two}, new Rule[] {one});
  }

  @Test
  void aPulseChannelIsNotAChannelNamedLikeIt() {
    Rule a = Rule.named("R").when(Channel.named("x pulse").above(1.0));
    Rule b = Rule.named("R").when(Channel.pulse("x").above(1.0));
    assertRejected(new Rule[] {a}, new Rule[] {b});
  }
}
