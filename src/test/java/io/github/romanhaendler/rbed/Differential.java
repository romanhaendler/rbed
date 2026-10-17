package io.github.romanhaendler.rbed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Runs two Detectors side by side on the same input and compares everything observable after
 * every push, advance and close: the callbacks (with the watched Expressions read inside each),
 * the complete Events (doubles bit for bit), {@code value(...)} of every watched Expression,
 * {@code retainedValues()}, {@code droppedValues()}, the warnings logged and the exception (type
 * and message) thrown for the input. A Side says how a Detector is made, so later tests can
 * compare a fresh Detector with a restored one.
 */
final class Differential {
  private Differential() {}

  /** One input to a Detector. */
  record Op(Kind kind, long timestamp, Map<String, Double> values) {
    enum Kind { PUSH, PUSH_VALUES, ADVANCE, CLOSE }

    /** {@code push(100, "a", 1.0, "b", 2.0)}: a Sample with timestamp. */
    static Op push(long timestamp, Object... channelsAndValues) {
      return new Op(Kind.PUSH, timestamp, pairs(channelsAndValues));
    }

    /** A Sample without timestamp, for a Detector with a sampling rate. */
    static Op pushValues(Object... channelsAndValues) {
      return new Op(Kind.PUSH_VALUES, 0, pairs(channelsAndValues));
    }

    static Op advance(long timestamp) {
      return new Op(Kind.ADVANCE, timestamp, Map.of());
    }

    static Op close() {
      return new Op(Kind.CLOSE, 0, Map.of());
    }

    private static Map<String, Double> pairs(Object[] kv) {
      Map<String, Double> m = new LinkedHashMap<>();
      for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
      return m;
    }

    void applyTo(Detector d) {
      Map<Channel, Double> m = new LinkedHashMap<>();
      values.forEach((k, v) -> m.put(Channel.named(k), v));
      switch (kind) {
        case PUSH -> d.push(timestamp, m);
        case PUSH_VALUES -> d.push(m);
        case ADVANCE -> d.advanceTo(timestamp);
        case CLOSE -> d.close();
      }
    }

    String java() {
      String vs = values.entrySet().stream()
          .map(e -> ", \"" + e.getKey() + "\", " + Scenario.lit(e.getValue()))
          .reduce("", String::concat);
      return switch (kind) {
        case PUSH -> "Op.push(" + timestamp + "L" + vs + ")";
        case PUSH_VALUES -> "Op.pushValues(" + (vs.isEmpty() ? "" : vs.substring(2)) + ")";
        case ADVANCE -> "Op.advance(" + timestamp + "L)";
        case CLOSE -> "Op.close()";
      };
    }

    @Override
    public String toString() {
      return switch (kind) {
        case PUSH -> "push(" + timestamp + ", " + values + ")";
        case PUSH_VALUES -> "push(" + values + ")";
        case ADVANCE -> "advanceTo(" + timestamp + ")";
        case CLOSE -> "close()";
      };
    }
  }

  /** How a Detector is made from the configuration; the listener is the one to report to. */
  interface Side {
    Detector build(Consumer<Detector.Builder> config, EventListener listener);
  }

  /** A fresh Detector built from the configuration. */
  static final Side FRESH = (config, listener) -> {
    Detector.Builder b = Detector.builder().listener(listener);
    config.accept(b);
    return b.build();
  };

  /** The first observable difference: after which moment, in what, and everything observed. */
  record Difference(int moment, String op, String field, String a, String b, List<String> allA,
      List<String> allB) {
    String report() {
      return "Difference after moment " + moment + " (" + op + ") in " + field + "\n  A: " + a
          + "\n  B: " + b + "\nObserved by A:\n  " + String.join("\n  ", allA)
          + "\nObserved by B:\n  " + String.join("\n  ", allB);
    }
  }

  /** {@code difference} is null when the two never differ; {@code built} is false if both rejected the set-up. */
  record Result(Difference difference, boolean built, int moments, Map<String, Integer> stats) {}

  /** The two sides of the fresh-versus-fresh comparison are the same; use it to prove the harness. */
  static void assertFresh(Consumer<Detector.Builder> config, List<Expression> watched, Op... ops) {
    Result r = compare(config, watched, List.of(ops), FRESH, FRESH);
    if (r.difference() != null) throw new AssertionError(r.difference().report());
  }

  /**
   * Runs the original through the first {@code k} ops, takes a Snapshot there, restores it into a
   * twin (built with {@code restoreConfig}, which must not set History) and compares the two from
   * the next op on, exactly as {@link #compare} does. Also checks that the twin starts with the
   * same {@code retainedValues}, {@code droppedValues} and watched values, that a Snapshot of
   * the twin equals the one it came from, and that the original is unchanged by taking one.
   */
  static Result compareRestoredAt(Consumer<Detector.Builder> config,
      Consumer<Detector.Builder> restoreConfig, List<Expression> watched, List<Op> ops, int k) {
    return capturingWarnings(warnings -> {
      Run a = new Run(FRESH, config, watched, warnings);
      if (a.detector == null) return new Result(null, false, 0, a.stats);
      for (int i = 0; i < k; i++) a.apply(ops.get(i));
      byte[] bytes = a.detector.snapshot();
      List<String> before = a.stateLines();
      Run b = new Run((c, listener) -> {
        Detector.Builder builder = Detector.builder().listener(listener);
        restoreConfig.accept(builder);
        return builder.restore(bytes).build();
      }, config, watched, warnings);
      if (b.detector == null) {
        return new Result(new Difference(k, "restore", "build", "ok", b.buildOutcome.toString(),
            before, b.buildOutcome), true, k, a.stats);
      }
      Difference d = diff(k, "restore", before, b.stateLines());
      if (d == null && !java.util.Arrays.equals(bytes, b.detector.snapshot())) {
        byte[] again = b.detector.snapshot();
        int at = java.util.Arrays.mismatch(bytes, again);
        d = new Difference(k, "restore", "snapshot round trip",
            "Snapshot of the original (" + bytes.length + " bytes)",
            "Snapshot of the restored (" + again.length + " bytes), first difference at byte " + at
                + " " + java.util.HexFormat.of().formatHex(bytes, Math.max(0, at - 8), Math.min(bytes.length, at + 16))
                + " vs " + java.util.HexFormat.of().formatHex(again, Math.max(0, at - 8), Math.min(bytes.length, at + 16)),
            before, b.stateLines());
      }
      if (d == null && !java.util.Arrays.equals(bytes, a.detector.snapshot())) {
        d = new Difference(k, "snapshot", "snapshot changed the Detector", "first", "second",
            before, a.stateLines());
      }
      if (d != null) return new Result(d, true, k, a.stats);
      for (int i = k; i < ops.size(); i++) {
        Op op = ops.get(i);
        List<String> oa = a.apply(op);
        List<String> ob = b.apply(op);
        d = diff(i + 1, op.toString(), oa, ob);
        if (d != null) return new Result(d, true, i + 1, a.stats);
      }
      return new Result(null, true, ops.size(), a.stats);
    });
  }

  /** How {@link #compareWithSnapshots} treats the Detector under test between ops. */
  enum Mode {
    /** Takes a Snapshot (twice, equal) and keeps using the same Detector. */
    SNAPSHOT_ONLY,
    /** Replaces the Detector by a restore of its own Snapshot. */
    RESTORE
  }

  /**
   * Runs an uninterrupted fresh Detector (A) against one (B) that, at the moments chosen by
   * {@code every} (every moment) or by {@code rng} (each moment with probability {@code p}), takes
   * a Snapshot and, in mode RESTORE, continues as a restore of it: with p = 1 a chain of restores
   * after every single moment, including before the first op. Every Snapshot of a restored
   * Detector must equal the one it was restored from.
   */
  static Result compareWithSnapshots(Consumer<Detector.Builder> config,
      Consumer<Detector.Builder> restoreConfig, List<Expression> watched, List<Op> ops, Mode mode,
      java.util.SplittableRandom rng, double p) {
    return capturingWarnings(warnings -> {
      Run a = new Run(FRESH, config, watched, warnings);
      if (a.detector == null) return new Result(null, false, 0, a.stats);
      Run b = new Run(FRESH, config, watched, warnings);
      for (int i = 0; i <= ops.size(); i++) {
        if (i > 0) {
          Op op = ops.get(i - 1);
          Difference d = diff(i, op.toString(), a.apply(op), b.apply(op));
          if (d != null) return new Result(d, true, i, a.stats);
        }
        if (p < 1 && rng.nextDouble() >= p) continue;
        byte[] bytes = b.detector.snapshot();
        List<String> before = b.stateLines();
        a.stats.merge("snapshot", 1, Integer::sum);
        Difference d = null;
        if (!java.util.Arrays.equals(bytes, b.detector.snapshot())) {
          d = new Difference(i, "snapshot", "two Snapshots in a row differ", "first", "second",
              before, b.stateLines());
        } else if (mode == Mode.RESTORE) {
          b.restoreFrom(restoreConfig, bytes);
          a.stats.merge("restore", 1, Integer::sum);
          d = diff(i, "restore", a.stateLines(), b.stateLines());
          if (d == null && !java.util.Arrays.equals(bytes, b.detector.snapshot())) {
            d = new Difference(i, "restore", "snapshot round trip", "original Snapshot",
                "Snapshot of the restored differs at byte "
                    + java.util.Arrays.mismatch(bytes, b.detector.snapshot()), before,
                b.stateLines());
          }
        }
        if (d != null) return new Result(d, true, i, a.stats);
      }
      return new Result(null, true, ops.size(), a.stats);
    });
  }

  private static Result capturingWarnings(java.util.function.Function<List<String>, Result> body) {
    Logger logger = Logger.getLogger("io.github.romanhaendler.rbed");
    List<String> warnings = new ArrayList<>();
    Handler handler = new Handler() {
      @Override public void publish(LogRecord r) { warnings.add(r.getLevel() + " " + r.getMessage()); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    boolean parents = logger.getUseParentHandlers();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
    try {
      return body.apply(warnings);
    } finally {
      logger.removeHandler(handler);
      logger.setUseParentHandlers(parents);
    }
  }

  static Result compare(Consumer<Detector.Builder> config, List<Expression> watched, List<Op> ops,
      Side sideA, Side sideB) {
    return capturing(warnings -> {
      Run a = new Run(sideA, config, watched, warnings);
      Run b = new Run(sideB, config, watched, warnings);
      Difference d = diff(0, "build", a.buildOutcome, b.buildOutcome);
      if (d != null || a.detector == null) return new Result(d, a.detector != null, 0, a.stats);
      for (int i = 0; i < ops.size(); i++) {
        Op op = ops.get(i);
        List<String> oa = a.apply(op);
        List<String> ob = b.apply(op);
        d = diff(i + 1, op.toString(), oa, ob);
        if (d != null) return new Result(d, true, i + 1, a.stats);
      }
      return new Result(null, true, ops.size(), a.stats);
    });
  }

  /** Runs {@code body} with the library's warnings collected into the list it is given. */
  static <T> T capturing(java.util.function.Function<List<String>, T> body) {
    Logger logger = Logger.getLogger("io.github.romanhaendler.rbed");
    List<String> warnings = new ArrayList<>();
    Handler handler = new Handler() {
      @Override public void publish(LogRecord r) { warnings.add(r.getLevel() + " " + r.getMessage()); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    boolean parents = logger.getUseParentHandlers();
    logger.setUseParentHandlers(false);
    logger.addHandler(handler);
    try {
      return body.apply(warnings);
    } finally {
      logger.removeHandler(handler);
      logger.setUseParentHandlers(parents);
    }
  }

  private static Difference diff(int moment, String op, List<String> a, List<String> b) {
    int n = Math.max(a.size(), b.size());
    for (int i = 0; i < n; i++) {
      String x = i < a.size() ? a.get(i) : "<nothing>";
      String y = i < b.size() ? b.get(i) : "<nothing>";
      if (!x.equals(y)) {
        String field = x.contains(":") ? x.substring(0, x.indexOf(':')) : y.substring(0, y.indexOf(':'));
        return new Difference(moment, op, field, x, y, a, b);
      }
    }
    return null;
  }

  /** One side: its Detector, what its listener heard, and the warnings logged for it. */
  static final class Run implements EventListener {
    final List<String> heard = new ArrayList<>();
    /** The Events that Ended on this side, in order. */
    final List<Event> ended = new ArrayList<>();
    /** Whether the latest {@link #apply} was accepted. */
    boolean lastOk;
    final List<Expression> watched;
    final List<String> warnings;
    Detector detector;
    final List<String> buildOutcome = new ArrayList<>();
    /** What happened on this side, counted: callbacks by kind, exceptions, warnings, defined watched values. */
    final Map<String, Integer> stats = new TreeMap<>();

    Run(Side side, Consumer<Detector.Builder> config, List<Expression> watched, List<String> warnings) {
      this.watched = watched;
      this.warnings = warnings;
      warnings.clear();
      try {
        detector = side.build(config, this);
        buildOutcome.add("outcome: ok");
      } catch (RuntimeException e) {
        buildOutcome.add("outcome: " + e.getClass().getName() + ": " + e.getMessage());
      }
      buildOutcome.addAll(observe());
    }

    /** Replaces the Detector by a restore of {@code bytes}, reporting to this Run. */
    void restoreFrom(Consumer<Detector.Builder> restoreConfig, byte[] bytes) {
      Detector.Builder builder = Detector.builder().listener(this);
      restoreConfig.accept(builder);
      detector = builder.restore(bytes).build();
    }

    /** What is observable between two moments: counters and watched values, no callbacks. */
    List<String> stateLines() {
      return observe().stream().filter(l -> l.startsWith("retainedValues")
          || l.startsWith("droppedValues") || l.startsWith("replayPoint")
          || l.startsWith("value(")).toList();
    }

    List<String> apply(Op op) {
      heard.clear();
      warnings.clear();
      String outcome;
      try {
        op.applyTo(detector);
        outcome = "ok";
      } catch (RuntimeException e) {
        outcome = e.getClass().getName() + ": " + e.getMessage();
      }
      lastOk = outcome.equals("ok");
      if (!outcome.equals("ok")) stats.merge("exception", 1, Integer::sum);
      stats.merge("warning", warnings.size(), Integer::sum);
      List<String> lines = new ArrayList<>();
      lines.add("outcome: " + outcome);
      lines.addAll(observe());
      return lines;
    }

    List<String> observe() {
      List<String> lines = new ArrayList<>();
      lines.add("callback count: " + heard.size());
      for (int i = 0; i < heard.size(); i++) lines.add("callback " + i + ": " + heard.get(i));
      lines.add("warning count: " + warnings.size());
      for (int i = 0; i < warnings.size(); i++) lines.add("warning " + i + ": " + warnings.get(i));
      if (detector != null) {
        lines.add("retainedValues: " + detector.retainedValues());
        lines.add("droppedValues: " + detector.droppedValues());
        lines.add("replayPoint: " + detector.replayPoint());
        for (Expression e : watched) lines.add("value(" + e + "): " + value(e));
      }
      return lines;
    }

    private String value(Expression e) {
      OptionalDouble v = detector.value(e);
      if (v.isPresent()) stats.merge("watchedValue", 1, Integer::sum);
      return v.isPresent() ? bits(v.getAsDouble()) : "empty";
    }

    private void hear(String kind, Event e) {
      stats.merge(kind, 1, Integer::sum);
      if (e.truncatedStart()) stats.merge("truncatedStart", 1, Integer::sum);
      if (e.truncatedEnd()) stats.merge("truncatedEnd", 1, Integer::sum);
      StringBuilder s = new StringBuilder(kind).append(' ').append(describe(e));
      if (detector != null && !watched.isEmpty()) {
        s.append(" | watched");
        for (Expression w : watched) s.append(' ').append(value(w));
      }
      heard.add(s.toString());
    }

    @Override public void onPending(Event e) { hear("pending", e); }
    @Override public void onConfirmed(Event e) { hear("confirmed", e); }
    @Override public void onEnded(Event e) { ended.add(e); hear("ended", e); }
    @Override public void onDiscarded(Event e) { hear("discarded", e); }
  }

  /** A double as decimal and raw bits, so NaN, -0.0 and +0.0 and the last bit are distinguished. */
  static String bits(double d) {
    return d + "/" + Long.toHexString(Double.doubleToRawLongBits(d));
  }

  static String describe(Event e) {
    StringBuilder s = new StringBuilder(e.name()).append(" start=").append(e.start())
        .append(" confirmed=").append(e.confirmed()).append(" end=").append(e.end())
        .append(" truncatedStart=").append(e.truncatedStart())
        .append(" truncatedEnd=").append(e.truncatedEnd());
    Map<String, Aggregates> sorted = new TreeMap<>();
    e.aggregates().forEach((c, a) -> sorted.put(c.name(), a));
    sorted.forEach((c, a) -> s.append(" ").append(c).append("=[min=").append(bits(a.min()))
        .append(" max=").append(bits(a.max())).append(" mean=").append(bits(a.mean()))
        .append(" count=").append(a.count()).append(" twMean=").append(bits(a.timeWeightedMean()))
        .append(" integral=").append(bits(a.integral())).append(']'));
    return s.toString();
  }

  /** Greedily replaces the scenario by a smaller one that still differs, until none does. */
  static Scenario shrink(Scenario s, Predicate<Scenario> differs) {
    Scenario current = s;
    for (int round = 0; round < 500; round++) {
      Scenario next = null;
      for (Scenario c : current.shrinks()) {
        if (differs.test(c)) {
          next = c;
          break;
        }
      }
      if (next == null) break;
      current = next;
    }
    return current;
  }

  /**
   * Compares two sides on a scenario. Returns null if they never differ (or the scenario cannot be
   * built, in which case {@code built[0]} stays false).
   */
  static Result run(Scenario s, Side a, Side b) {
    Scenario.Built built;
    try {
      built = s.build();
    } catch (RuntimeException e) {
      return new Result(null, false, 0, Map.of());
    }
    return compare(built.config(), built.watched(), s.ops(), a, b);
  }

  /** Fails with the seed, the difference and the shrunk scenario as JUnit source. */
  static void assertNoDifference(Scenario s, Side a, Side b) {
    Result r = run(s, a, b);
    if (r.difference() == null) return;
    Scenario small = shrink(s, c -> run(c, a, b).difference() != null);
    Result sr = run(small, a, b);
    throw new AssertionError("Seed " + s.seed() + " (rerun with -Drbed.diff.seed=" + s.seed() + ")\n"
        + sr.difference().report() + "\n\nShrunk to a regression test (paste into DifferentialTest):\n"
        + small.toJava("regression_seed_" + s.seed()));
  }
}
