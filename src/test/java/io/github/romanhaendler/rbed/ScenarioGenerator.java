package io.github.romanhaendler.rbed;

import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.Scenario.HistorySpec;
import io.github.romanhaendler.rbed.Scenario.Node;
import io.github.romanhaendler.rbed.Scenario.RuleSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Builds random Scenarios from a seed: Rule sets from every construct the library offers and
 * inputs that hit its edges. The same seed always gives the same Scenario.
 */
final class ScenarioGenerator {
  private static final String[] CHANNELS = {"a", "b", "c"};
  private static final String PULSE = "p";
  private static final double[] THRESHOLDS = {0, 1, 2, 3, 5, 10, 0.5, -1, 7.5};

  private final SplittableRandom r;
  private final long seed;
  private int limit; // Retention Limit, 0 = default
  private final List<String> ruleNames = new ArrayList<>();
  private final List<Long> timeConstants = new ArrayList<>(List.of(1L, 10L, 50L, 100L, 250L));

  private ScenarioGenerator(long seed) {
    this.seed = seed;
    r = new SplittableRandom(seed);
  }

  static Scenario generate(long seed) {
    return new ScenarioGenerator(seed).scenario();
  }

  private Scenario scenario() {
    double hz = r.nextInt(8) == 0 ? new double[] {1, 2, 10, 50, 1000}[r.nextInt(5)] : 0;
    limit = r.nextInt(3) == 0 ? 1 + r.nextInt(5) : 0;
    List<RuleSpec> rules = new ArrayList<>();
    int count = 1 + r.nextInt(4);
    for (int i = 0; i < count; i++) {
      rules.add(rule("R" + i));
      ruleNames.add("R" + i);
    }
    List<Node> watched = new ArrayList<>();
    for (int i = r.nextInt(4); i > 0; i--) watched.add(number(2, count));
    List<HistorySpec> history = r.nextInt(6) == 0 ? history(count) : List.of();
    Scenario s = new Scenario(seed, hz, limit, rules, watched, history, List.of());
    return s.withOps(ops(s, hz > 0));
  }

  private RuleSpec rule(String name) {
    int before = ruleNames.size();
    Node when = condition(2, before);
    int forSamples = r.nextInt(5) < 2 ? 1 + r.nextInt(4) : 0;
    long forDuration = r.nextInt(10) < 3 ? duration() : -1;
    Node endWhen = null;
    int endSamples = 0;
    long endDuration = -1;
    if (r.nextInt(10) < 3) {
      endWhen = condition(2, before);
      endSamples = r.nextInt(5) == 0 ? 1 + r.nextInt(3) : 0;
      endDuration = r.nextInt(5) == 0 ? duration() : -1;
    }
    long maxGap = r.nextInt(4) == 0 ? timeConstant(30, 600) : -1;
    if (maxGap >= 0) timeConstants.add(maxGap);
    List<String> aggregate = new ArrayList<>();
    if (r.nextInt(10) < 3) {
      for (String c : CHANNELS) if (r.nextBoolean()) aggregate.add(c);
    }
    return new RuleSpec(name, when, forSamples, forDuration, endWhen, endSamples, endDuration,
        maxGap, aggregate);
  }

  private long duration() {
    long d = r.nextInt(8) == 0 ? 0 : timeConstant(1, 800);
    timeConstants.add(d);
    return d;
  }

  private long timeConstant(int low, int high) {
    return low + r.nextInt(high - low + 1);
  }

  private double threshold() {
    return THRESHOLDS[r.nextInt(THRESHOLDS.length)];
  }

  private String ruleName(int before) {
    return ruleNames.get(r.nextInt(before));
  }

  private Node channel() {
    return r.nextInt(6) == 0 ? Node.named("pulse", PULSE, 0) : Node.named("ch", CHANNELS[r.nextInt(3)], 0);
  }

  /** A Channel or a moving average over one. */
  private Node channelExpression(int depth) {
    Node base = channel();
    if (depth > 0 && r.nextInt(4) == 0) {
      boolean samples = r.nextBoolean();
      int n = 1 + r.nextInt(6);
      if (limit > 0) n = Math.min(n, limit);
      Node avg = samples ? Node.ofA("avgS", n, base) : Node.ofA("avgD", r.nextInt(8) == 0 ? 0 : timeConstant(1, 800), base);
      return depth > 1 && r.nextInt(6) == 0 ? Node.ofA("avgS", Math.max(1, Math.min(3, limit == 0 ? 3 : limit)), avg) : avg;
    }
    return base;
  }

  private Node number(int depth, int rulesBefore) {
    int p = r.nextInt(100);
    if (depth == 0 || p < 35) return channelExpression(depth);
    if (p < 43) return Node.of("rate", channelExpression(1));
    if (p < 51) return Node.of("age", channelExpression(1));
    if (p < 61) {
      String[] ops = {"plus", "minus", "times", "div"};
      return Node.of(ops[r.nextInt(4)], number(depth - 1, rulesBefore), number(depth - 1, rulesBefore));
    }
    if (p < 71) {
      String[] ops = {"plusC", "minusC", "timesC", "divC"};
      double c = r.nextInt(12) == 0 ? 0 : threshold();
      return Node.ofA(ops[r.nextInt(4)], c, number(depth - 1, rulesBefore));
    }
    if (p < 75) return Node.of("abs", number(depth - 1, rulesBefore));
    if (rulesBefore == 0) return channelExpression(depth);
    String rule = ruleName(rulesBefore);
    if (p < 81) return Node.named("count", rule, r.nextBoolean() ? -1 : timeConstant(1, 1500));
    if (p < 85) return Node.named("total", rule, 0);
    if (p < 99) {
      String[] ops = {"latestMin", "latestMax", "latestMean"};
      return Node.named(ops[r.nextInt(3)], rule, 0, CHANNELS[r.nextInt(3)]);
    }
    return Node.named("latestDur", rule, 0);
  }

  private Node condition(int depth, int rulesBefore) {
    int p = r.nextInt(100);
    if (depth == 0 || p < 40) {
      Node e = number(Math.min(depth, 1), rulesBefore);
      return Node.ofA(r.nextBoolean() ? "aboveC" : "belowC", threshold(), e);
    }
    if (p < 50) return Node.of(r.nextBoolean() ? "above" : "below", number(1, rulesBefore), number(1, rulesBefore));
    if (p < 58) {
      double lo = threshold();
      return Node.ofAB("between", lo, lo + r.nextInt(4), number(1, rulesBefore));
    }
    if (p < 68) return Node.of(r.nextBoolean() ? "rising" : "falling", channelExpression(1));
    if (p < 80) return Node.of(r.nextBoolean() ? "and" : "or", condition(depth - 1, rulesBefore), condition(depth - 1, rulesBefore));
    if (p < 85) return Node.of("not", condition(depth - 1, rulesBefore));
    if (rulesBefore == 0) return Node.ofA("aboveC", threshold(), channelExpression(1));
    if (p < 93) return Node.named("during", ruleName(rulesBefore), 0);
    List<String> unless = new ArrayList<>();
    for (int i = r.nextInt(3); i > 0; i--) unless.add(ruleName(rulesBefore));
    return new Node("after", ruleName(rulesBefore), r.nextBoolean() ? -1 : timeConstant(1, 1500), 0,
        List.of(), unless);
  }

  private List<HistorySpec> history(int ruleCount) {
    List<HistorySpec> out = new ArrayList<>();
    long t = -9000;
    for (int i = 0; i < ruleCount; i++) {
      if (r.nextBoolean()) continue;
      for (int k = 1 + r.nextInt(2); k > 0; k--) {
        long start = t + 1 + r.nextInt(500);
        long confirmed = start + r.nextInt(300);
        long end = confirmed + r.nextInt(300);
        out.add(new HistorySpec("R" + i, start, confirmed, end));
        t = end + 1;
      }
    }
    return out;
  }

  private List<Op> ops(Scenario s, boolean sampling) {
    List<Double> special = new ArrayList<>(s.constants());
    for (double t : THRESHOLDS) special.add(t);
    List<Op> ops = new ArrayList<>();
    long t = r.nextInt(3) == 0 ? r.nextInt(1000) : 0;
    long last = Long.MIN_VALUE;
    int n = 5 + r.nextInt(60);
    int closeAt = r.nextInt(5) == 0 ? n - r.nextInt(Math.min(n, 10)) - 1 : -1;
    for (int i = 0; i < n; i++) {
      int p = r.nextInt(100);
      if (!sampling) t += step();
      if (i == closeAt) { // a few more inputs may follow, which the closed Detector rejects
        ops.add(Op.close());
        n = Math.min(n, i + 1 + r.nextInt(3));
        continue;
      }
      if (p < 6) {
        ops.add(invalid(sampling, last, special));
      } else if (p < 14 && !sampling) {
        ops.add(Op.advance(t));
        last = t;
      } else {
        Map<String, Double> values = values(special);
        if (sampling) {
          ops.add(new Op(Op.Kind.PUSH_VALUES, 0, values));
        } else {
          ops.add(new Op(Op.Kind.PUSH, t, values));
          last = t;
        }
      }
    }
    return ops;
  }

  private long step() {
    long base = timeConstants.get(r.nextInt(timeConstants.size()));
    return Math.max(1, base + (r.nextInt(6) == 0 ? r.nextInt(3) - 1 : 0));
  }

  private Op invalid(boolean sampling, long last, List<Double> special) {
    int k = r.nextInt(5);
    Map<String, Double> values = values(special);
    if (k == 0) values.put(CHANNELS[r.nextInt(3)], Double.NaN);
    else if (k == 1) values.put(CHANNELS[r.nextInt(3)], r.nextBoolean() ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
    if (sampling) return k >= 2 ? Op.push(r.nextInt(100), "a", 1.0) : new Op(Op.Kind.PUSH_VALUES, 0, values);
    if (k == 2) return Op.advance(Math.max(0, last) - r.nextInt(3)); // not after the previous moment
    long ts = k == 3 ? last : last == Long.MIN_VALUE ? 0 : last - r.nextInt(100);
    return new Op(Op.Kind.PUSH, ts, values);
  }

  private Map<String, Double> values(List<Double> special) {
    Map<String, Double> m = new LinkedHashMap<>();
    for (String c : CHANNELS) if (r.nextInt(4) != 0) m.put(c, value(special));
    if (r.nextInt(3) == 0) m.put(PULSE, value(special));
    return m;
  }

  private double value(List<Double> special) {
    int p = r.nextInt(100);
    if (p < 35 && !special.isEmpty()) return special.get(r.nextInt(special.size()));
    if (p < 50 && !special.isEmpty()) {
      double t = special.get(r.nextInt(special.size()));
      return r.nextBoolean() ? Math.nextUp(t) : Math.nextDown(t);
    }
    if (p < 70) return r.nextInt(12);
    if (p < 75) return -0.0;
    if (p < 78) return r.nextBoolean() ? 1e300 : -1e300;
    if (p < 82) return 1e-300;
    return Math.round(r.nextGaussian() * 40) / 8.0;
  }
}
