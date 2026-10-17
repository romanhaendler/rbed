package io.github.romanhaendler.rbed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A Detector set-up and an input as plain data, so that a generator can create it, a shrinker can
 * make it smaller and {@link #toJava} can print it as a JUnit test. Public API only.
 */
record Scenario(
    long seed,
    double samplingRate,
    int retentionLimit,
    List<RuleSpec> rules,
    List<Node> watched,
    List<HistorySpec> history,
    List<Differential.Op> ops) {

  /** The configuration of a Detector Builder plus the Expressions it watches. */
  record Built(Consumer<Detector.Builder> config, List<Expression> watched) {}

  /** A Rule as data; 0 / -1 / null mean "not set". */
  record RuleSpec(
      String name,
      Node when,
      int forSamples,
      long forDuration,
      Node endWhen,
      int endSamples,
      long endDuration,
      long maxGap,
      List<String> aggregate) {

    Rule build(Map<String, Rule> known) {
      Rule rule = Rule.named(name).when(Node.condition(when, known));
      if (forSamples > 0) rule = rule.forSamples(forSamples);
      if (forDuration >= 0) rule = rule.forDuration(forDuration);
      if (endWhen != null) {
        rule = rule.endWhen(Node.condition(endWhen, known));
        if (endSamples > 0) rule = rule.forSamples(endSamples);
        if (endDuration >= 0) rule = rule.forDuration(endDuration);
      }
      if (maxGap >= 0) rule = rule.maxGap(maxGap);
      if (!aggregate.isEmpty()) {
        rule = rule.aggregate(aggregate.stream().map(Channel::named).toArray(Channel[]::new));
      }
      return rule;
    }

    String java() {
      StringBuilder s = new StringBuilder("Rule.named(\"" + name + "\").when(" + when.java() + ")");
      if (forSamples > 0) s.append(".forSamples(").append(forSamples).append(')');
      if (forDuration >= 0) s.append(".forDuration(").append(forDuration).append("L)");
      if (endWhen != null) {
        s.append(".endWhen(").append(endWhen.java()).append(')');
        if (endSamples > 0) s.append(".forSamples(").append(endSamples).append(')');
        if (endDuration >= 0) s.append(".forDuration(").append(endDuration).append("L)");
      }
      if (maxGap >= 0) s.append(".maxGap(").append(maxGap).append("L)");
      if (!aggregate.isEmpty()) {
        s.append(".aggregate(")
            .append(aggregate.stream().map(c -> "Channel.named(\"" + c + "\")")
                .collect(Collectors.joining(", ")))
            .append(')');
      }
      return s.toString();
    }
  }

  /** An Event known from before the Detector started. */
  record HistorySpec(String rule, long start, long confirmed, long end) {
    Event build() {
      return new Event(rule, start, OptionalLong.of(confirmed), OptionalLong.of(end), false, false,
          Map.of());
    }

    String java() {
      return "new Event(\"" + rule + "\", " + start + "L, OptionalLong.of(" + confirmed
          + "L), OptionalLong.of(" + end + "L), false, false, Map.of())";
    }
  }

  /**
   * A Condition or Expression as a tree. {@code a} and {@code b} are numeric parameters, {@code
   * name} a Channel or Rule name, {@code names} further names. A tree of the wrong shape (for
   * example after the shrinker promoted a sub-tree) fails to build with the same exception
   * everywhere, which counts as "not a difference".
   */
  record Node(String op, String name, double a, double b, List<Node> kids, List<String> names) {
    static Node of(String op, Node... kids) {
      return new Node(op, null, 0, 0, List.of(kids), List.of());
    }

    static Node ofA(String op, double a, Node... kids) {
      return new Node(op, null, a, 0, List.of(kids), List.of());
    }

    static Node ofAB(String op, double a, double b, Node... kids) {
      return new Node(op, null, a, b, List.of(kids), List.of());
    }

    static Node named(String op, String name, double a, String... names) {
      return new Node(op, name, a, 0, List.of(), List.of(names));
    }

    private static IllegalArgumentException shape() {
      return new IllegalArgumentException("shape");
    }

    static Condition condition(Node n, Map<String, Rule> known) {
      return n.build(known) instanceof Condition c ? c : fail();
    }

    private static <T> T fail() {
      throw shape();
    }

    private Expression ex(int i, Map<String, Rule> known) {
      if (i >= kids.size()) throw shape();
      return kids.get(i).build(known) instanceof Expression e ? e : fail();
    }

    private ChannelExpression ce(int i, Map<String, Rule> known) {
      if (i >= kids.size()) throw shape();
      return kids.get(i).build(known) instanceof ChannelExpression e ? e : fail();
    }

    private Condition co(int i, Map<String, Rule> known) {
      if (i >= kids.size()) throw shape();
      return condition(kids.get(i), known);
    }

    private Rule rule(Map<String, Rule> known) {
      Rule r = known.get(name);
      if (r == null) throw new IllegalArgumentException("unknown rule " + name);
      return r;
    }

    Object build(Map<String, Rule> k) {
      return switch (op) {
        case "ch" -> Channel.named(name);
        case "pulse" -> Channel.pulse(name);
        case "avgS" -> ce(0, k).averageOverSamples((int) a);
        case "avgD" -> ce(0, k).averageOverDuration((long) a);
        case "rate" -> ce(0, k).rate();
        case "age" -> ce(0, k).age();
        case "rising" -> ce(0, k).rising();
        case "falling" -> ce(0, k).falling();
        case "abs" -> ex(0, k).abs();
        case "plus" -> ex(0, k).plus(ex(1, k));
        case "minus" -> ex(0, k).minus(ex(1, k));
        case "times" -> ex(0, k).times(ex(1, k));
        case "div" -> ex(0, k).dividedBy(ex(1, k));
        case "plusC" -> ex(0, k).plus(a);
        case "minusC" -> ex(0, k).minus(a);
        case "timesC" -> ex(0, k).times(a);
        case "divC" -> ex(0, k).dividedBy(a);
        case "above" -> ex(0, k).above(ex(1, k));
        case "below" -> ex(0, k).below(ex(1, k));
        case "aboveC" -> ex(0, k).above(a);
        case "belowC" -> ex(0, k).below(a);
        case "between" -> ex(0, k).between(a, b);
        case "and" -> co(0, k).and(co(1, k));
        case "or" -> co(0, k).or(co(1, k));
        case "not" -> co(0, k).not();
        case "during" -> Conditions.during(rule(k));
        case "after" -> {
          After after = Conditions.after(rule(k));
          if (!names.isEmpty()) {
            after = after.unless(names.stream().map(n -> {
              Rule r = k.get(n);
              if (r == null) throw new IllegalArgumentException("unknown rule " + n);
              return r;
            }).toArray(Rule[]::new));
          }
          yield a >= 0 ? after.within((long) a) : after;
        }
        case "count" -> a >= 0 ? Conditions.count(rule(k)).within((long) a) : Conditions.count(rule(k));
        case "total" -> Conditions.totalDuration(rule(k));
        case "latestMin" -> Conditions.latest(rule(k)).min(Channel.named(names.get(0)));
        case "latestMax" -> Conditions.latest(rule(k)).max(Channel.named(names.get(0)));
        case "latestMean" -> Conditions.latest(rule(k)).mean(Channel.named(names.get(0)));
        case "latestDur" -> Conditions.latest(rule(k)).duration();
        case "const" -> throw shape(); // only used as an operand through the "C" variants
        default -> throw new IllegalStateException("unknown op " + op);
      };
    }

    /** Java source for this tree; rules are referred to by variables named like the Rule, lower-cased. */
    String java() {
      String k0 = kids.isEmpty() ? "?" : kids.get(0).java();
      String k1 = kids.size() < 2 ? "?" : kids.get(1).java();
      String r = name == null ? "?" : name.toLowerCase();
      return switch (op) {
        case "ch" -> "Channel.named(\"" + name + "\")";
        case "pulse" -> "Channel.pulse(\"" + name + "\")";
        case "avgS" -> k0 + ".averageOverSamples(" + (int) a + ")";
        case "avgD" -> k0 + ".averageOverDuration(" + (long) a + "L)";
        case "rate", "age", "rising", "falling", "abs" -> k0 + "." + op + "()";
        case "not" -> k0 + ".not()";
        case "plus", "minus", "times", "above", "below", "and", "or" ->
            k0 + "." + op + "(" + k1 + ")";
        case "div" -> k0 + ".dividedBy(" + k1 + ")";
        case "plusC", "minusC", "timesC", "aboveC", "belowC" ->
            k0 + "." + op.substring(0, op.length() - 1) + "(" + lit(a) + ")";
        case "divC" -> k0 + ".dividedBy(" + lit(a) + ")";
        case "between" -> k0 + ".between(" + lit(a) + ", " + lit(b) + ")";
        case "during" -> "Conditions.during(" + r + ")";
        case "after" -> "Conditions.after(" + r + ")"
            + (names.isEmpty() ? "" : ".unless("
                + names.stream().map(String::toLowerCase).collect(Collectors.joining(", ")) + ")")
            + (a >= 0 ? ".within(" + (long) a + "L)" : "");
        case "count" -> "Conditions.count(" + r + ")" + (a >= 0 ? ".within(" + (long) a + "L)" : "");
        case "total" -> "Conditions.totalDuration(" + r + ")";
        case "latestMin" -> "Conditions.latest(" + r + ").min(Channel.named(\"" + names.get(0) + "\"))";
        case "latestMax" -> "Conditions.latest(" + r + ").max(Channel.named(\"" + names.get(0) + "\"))";
        case "latestMean" -> "Conditions.latest(" + r + ").mean(Channel.named(\"" + names.get(0) + "\"))";
        case "latestDur" -> "Conditions.latest(" + r + ").duration()";
        default -> op + "?";
      };
    }

    /** Smaller trees: each child in place of this node, and this node with a smaller child. */
    List<Node> shrinks() {
      List<Node> out = new ArrayList<>(kids);
      for (int i = 0; i < kids.size(); i++) {
        for (Node s : kids.get(i).shrinks()) {
          List<Node> copy = new ArrayList<>(kids);
          copy.set(i, s);
          out.add(new Node(op, name, a, b, copy, names));
        }
      }
      return out;
    }

    void collectConstants(List<Double> into) {
      if (op.endsWith("C") || op.equals("between")) {
        into.add(a);
        if (op.equals("between")) into.add(b);
      }
      if (op.equals("count") && a >= 0) into.add(a);
      kids.forEach(kid -> kid.collectConstants(into));
    }
  }

  static String lit(double d) {
    if (Double.isNaN(d)) return "Double.NaN";
    if (d == Double.POSITIVE_INFINITY) return "Double.POSITIVE_INFINITY";
    if (d == Double.NEGATIVE_INFINITY) return "Double.NEGATIVE_INFINITY";
    return Double.toString(d);
  }

  /**
   * Builds the Rules and Expressions once; the returned configuration can configure any number of
   * Builders.
   *
   * @throws RuntimeException if the scenario cannot be built (a shape or reference error)
   */
  Built build() {
    Map<String, Rule> known = new LinkedHashMap<>();
    for (RuleSpec spec : rules) known.put(spec.name(), spec.build(known));
    Rule[] ruleArray = known.values().toArray(Rule[]::new);
    List<Expression> exprs = new ArrayList<>();
    for (Node n : watched) {
      if (!(n.build(known) instanceof Expression e)) throw new IllegalArgumentException("shape");
      exprs.add(e);
    }
    List<Event> hist = history.stream().map(HistorySpec::build).toList();
    Consumer<Detector.Builder> config = b -> {
      b.rules(ruleArray).watch(exprs.toArray(Expression[]::new));
      if (samplingRate > 0) b.samplingRate(samplingRate);
      if (retentionLimit > 0) b.retentionLimit(retentionLimit);
      if (!hist.isEmpty()) b.history(hist);
    };
    return new Built(config, exprs);
  }

  /** Constants that appear in the Rules, so inputs can hit thresholds exactly. */
  List<Double> constants() {
    List<Double> out = new ArrayList<>();
    for (RuleSpec r : rules) {
      r.when.collectConstants(out);
      if (r.endWhen != null) r.endWhen.collectConstants(out);
    }
    watched.forEach(w -> w.collectConstants(out));
    return out;
  }

  /** A JUnit test method that reproduces this scenario; paste it into DifferentialTest. */
  String toJava(String testName) {
    StringBuilder s = new StringBuilder();
    s.append("  @Test\n  void ").append(testName).append("() {\n");
    for (RuleSpec r : rules) {
      s.append("    Rule ").append(r.name().toLowerCase()).append(" = ").append(r.java()).append(";\n");
    }
    for (int i = 0; i < watched.size(); i++) {
      s.append("    Expression w").append(i).append(" = ").append(watched.get(i).java()).append(";\n");
    }
    s.append("    Differential.assertFresh(\n        b -> b.rules(")
        .append(rules.stream().map(r -> r.name().toLowerCase()).collect(Collectors.joining(", ")))
        .append(')');
    if (!watched.isEmpty()) {
      s.append(".watch(")
          .append(java.util.stream.IntStream.range(0, watched.size()).mapToObj(i -> "w" + i)
              .collect(Collectors.joining(", ")))
          .append(')');
    }
    if (samplingRate > 0) s.append(".samplingRate(").append(lit(samplingRate)).append(')');
    if (retentionLimit > 0) s.append(".retentionLimit(").append(retentionLimit).append(')');
    if (!history.isEmpty()) {
      s.append(".history(List.of(")
          .append(history.stream().map(HistorySpec::java).collect(Collectors.joining(", ")))
          .append("))");
    }
    s.append(",\n        List.of(")
        .append(java.util.stream.IntStream.range(0, watched.size()).mapToObj(i -> "w" + i)
            .collect(Collectors.joining(", ")))
        .append(')');
    for (Differential.Op op : ops) s.append(",\n        ").append(op.java());
    s.append(");\n  }\n");
    return s.toString();
  }

  @Override
  public String toString() {
    return "Scenario[seed=" + seed + ", rules=" + rules.size() + ", ops=" + ops.size() + "]";
  }

  Scenario withOps(List<Differential.Op> o) {
    return new Scenario(seed, samplingRate, retentionLimit, rules, watched, history, o);
  }

  Scenario withRules(List<RuleSpec> r) {
    return new Scenario(seed, samplingRate, retentionLimit, r, watched, history, ops);
  }

  Scenario withWatched(List<Node> w) {
    return new Scenario(seed, samplingRate, retentionLimit, rules, w, history, ops);
  }

  Scenario withHistory(List<HistorySpec> h) {
    return new Scenario(seed, samplingRate, retentionLimit, rules, watched, h, ops);
  }

  Scenario withRetention(int limit) {
    return new Scenario(seed, samplingRate, limit, rules, watched, history, ops);
  }

  /** Candidates that are strictly smaller or simpler, most promising first. */
  List<Scenario> shrinks() {
    List<Scenario> out = new ArrayList<>();
    // ops: drop chunks, then single ops
    for (int chunk = Math.max(1, ops.size() / 2); chunk >= 1; chunk /= 2) {
      for (int from = 0; from + chunk <= ops.size(); from += chunk) {
        List<Differential.Op> o = new ArrayList<>(ops);
        o.subList(from, from + chunk).clear();
        out.add(withOps(o));
      }
      if (chunk == 1) break;
    }
    for (int i = 0; i < watched.size(); i++) {
      List<Node> w = new ArrayList<>(watched);
      w.remove(i);
      out.add(withWatched(w));
    }
    for (int i = 0; i < history.size(); i++) {
      List<HistorySpec> h = new ArrayList<>(history);
      h.remove(i);
      out.add(withHistory(h));
    }
    for (int i = 0; i < rules.size(); i++) {
      List<RuleSpec> r = new ArrayList<>(rules);
      r.remove(i);
      out.add(withRules(r));
    }
    if (retentionLimit > 0) out.add(withRetention(0));
    for (int i = 0; i < rules.size(); i++) {
      RuleSpec s = rules.get(i);
      List<RuleSpec> variants = new ArrayList<>();
      if (s.forSamples > 0) variants.add(new RuleSpec(s.name, s.when, 0, s.forDuration, s.endWhen, s.endSamples, s.endDuration, s.maxGap, s.aggregate));
      if (s.forDuration >= 0) variants.add(new RuleSpec(s.name, s.when, s.forSamples, -1, s.endWhen, s.endSamples, s.endDuration, s.maxGap, s.aggregate));
      if (s.endWhen != null) variants.add(new RuleSpec(s.name, s.when, s.forSamples, s.forDuration, null, 0, -1, s.maxGap, s.aggregate));
      if (s.endSamples > 0) variants.add(new RuleSpec(s.name, s.when, s.forSamples, s.forDuration, s.endWhen, 0, s.endDuration, s.maxGap, s.aggregate));
      if (s.endDuration >= 0) variants.add(new RuleSpec(s.name, s.when, s.forSamples, s.forDuration, s.endWhen, s.endSamples, -1, s.maxGap, s.aggregate));
      if (s.maxGap >= 0) variants.add(new RuleSpec(s.name, s.when, s.forSamples, s.forDuration, s.endWhen, s.endSamples, s.endDuration, -1, s.aggregate));
      if (!s.aggregate.isEmpty()) variants.add(new RuleSpec(s.name, s.when, s.forSamples, s.forDuration, s.endWhen, s.endSamples, s.endDuration, s.maxGap, List.of()));
      for (Node w : s.when.shrinks()) variants.add(new RuleSpec(s.name, w, s.forSamples, s.forDuration, s.endWhen, s.endSamples, s.endDuration, s.maxGap, s.aggregate));
      if (s.endWhen != null) {
        for (Node w : s.endWhen.shrinks()) variants.add(new RuleSpec(s.name, s.when, s.forSamples, s.forDuration, w, s.endSamples, s.endDuration, s.maxGap, s.aggregate));
      }
      for (RuleSpec v : variants) {
        List<RuleSpec> r = new ArrayList<>(rules);
        r.set(i, v);
        out.add(withRules(r));
      }
    }
    for (int i = 0; i < watched.size(); i++) {
      for (Node w : watched.get(i).shrinks()) {
        List<Node> l = new ArrayList<>(watched);
        l.set(i, w);
        out.add(withWatched(l));
      }
    }
    for (int i = 0; i < ops.size(); i++) {
      Differential.Op op = ops.get(i);
      for (String key : op.values().keySet()) {
        Map<String, Double> m = new LinkedHashMap<>(op.values());
        m.remove(key);
        List<Differential.Op> o = new ArrayList<>(ops);
        o.set(i, new Differential.Op(op.kind(), op.timestamp(), m));
        out.add(withOps(o));
      }
    }
    return out;
  }
}
