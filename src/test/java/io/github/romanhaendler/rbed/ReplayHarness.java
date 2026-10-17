package io.github.romanhaendler.rbed;

import io.github.romanhaendler.rbed.Differential.Difference;
import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.Differential.Run;
import io.github.romanhaendler.rbed.Differential.Side;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * Checks the Replay Point at every stop point of an input. One Detector runs the whole input and
 * records what it observes. For a stop after moment k, a second Detector is built with the
 * History of the Events Ended before the Replay Point, fed the input from the Replay Point to the
 * stop (what it reports is dropped), and from the stop on everything it observes must equal what
 * the first one observed. {@code droppedValues()} is not compared: it counts since the Detector
 * was built, so a replay cannot reproduce it (see the Replay Point's preconditions).
 */
final class ReplayHarness {
  private ReplayHarness() {}

  /** What the uninterrupted run observed, per moment (index 0 is the build). */
  record Original(Consumer<Detector.Builder> config, List<Expression> watched, List<Op> ops,
      List<OptionalLong> points, List<Long> dropped, List<Integer> endedCount, List<Event> ended,
      List<Boolean> accepted, List<List<String>> lines, List<List<String>> state) {}

  /** Counts over all stops of one input; {@code pinned}: the Replay Point is still the first moment. */
  record Stats(int stops, int checked, int skippedDropped, int empty, int teeth, int pinned) {
    static final Stats NONE = new Stats(0, 0, 0, 0, 0, 0);

    Stats plus(Stats o) {
      return new Stats(stops + o.stops, checked + o.checked, skippedDropped + o.skippedDropped,
          empty + o.empty, teeth + o.teeth, pinned + o.pinned);
    }
  }

  /** A failure: the stop, the replay start, and what differed. */
  record Failure(int stop, long from, Difference difference) {
    String report() {
      return "Replay from " + from + " for a stop after moment " + stop + "\n"
          + difference.report();
    }
  }

  record Outcome(Stats stats, Failure failure) {}

  static Original run(Consumer<Detector.Builder> config, List<Expression> watched, List<Op> ops) {
    return Differential.capturing(warnings -> {
      Run run = new Run(Differential.FRESH, config, watched, warnings);
      List<OptionalLong> points = new ArrayList<>();
      List<Long> dropped = new ArrayList<>();
      List<Integer> endedCount = new ArrayList<>();
      List<Boolean> accepted = new ArrayList<>();
      List<List<String>> lines = new ArrayList<>();
      List<List<String>> state = new ArrayList<>();
      points.add(OptionalLong.empty());
      dropped.add(0L);
      endedCount.add(0);
      accepted.add(true);
      lines.add(List.of());
      run.heard.clear();
      warnings.clear();
      state.add(run.observe());
      for (Op op : ops) {
        lines.add(run.apply(op));
        accepted.add(run.lastOk);
        run.heard.clear();
        warnings.clear();
        state.add(run.observe());
        points.add(run.detector.replayPoint());
        dropped.add(run.detector.droppedValues());
        endedCount.add(run.ended.size());
      }
      return new Original(config, watched, ops, points, dropped, endedCount,
          List.copyOf(run.ended), accepted, lines, state);
    });
  }

  /**
   * Replays from {@code from} for a stop after moment {@code stop} and returns the first
   * difference, or null.
   */
  static Difference replay(Original o, int stop, long from) {
    return Differential.capturing(warnings -> {
      List<Event> history = new ArrayList<>();
      for (Event e : o.ended().subList(0, o.endedCount().get(stop))) {
        if (e.end().getAsLong() < from) history.add(e);
      }
      Side side = (config, listener) -> {
        Detector.Builder b = Detector.builder().listener(listener);
        config.accept(b);
        return b.history(history).build();
      };
      Run b = new Run(side, o.config(), o.watched(), warnings);
      for (int i = 0; i < stop; i++) {
        Op op = o.ops().get(i);
        if (!o.accepted().get(i + 1)) continue;
        if (op.kind() == Op.Kind.CLOSE || op.timestamp() >= from) b.apply(op);
      }
      b.heard.clear();
      warnings.clear();
      Difference d = compare(stop, "the stop", o.state().get(stop), b.observe());
      for (int i = stop; d == null && i < o.ops().size(); i++) {
        d = compare(i + 1, o.ops().get(i).toString(), o.lines().get(i + 1), b.apply(o.ops().get(i)));
      }
      return d;
    });
  }

  /** The replayed Detector has a Replay Point of its own, and does not reproduce the dropped count. */
  private static boolean compared(String line) {
    return !line.startsWith("droppedValues:") && !line.startsWith("replayPoint:");
  }

  private static Difference compare(int moment, String op, List<String> a, List<String> b) {
    List<String> x = a.stream().filter(ReplayHarness::compared).toList();
    List<String> y = b.stream().filter(ReplayHarness::compared).toList();
    int n = Math.max(x.size(), y.size());
    for (int i = 0; i < n; i++) {
      String p = i < x.size() ? x.get(i) : "<nothing>";
      String q = i < y.size() ? y.get(i) : "<nothing>";
      if (!p.equals(q)) {
        return new Difference(moment, op, p.substring(0, Math.max(0, p.indexOf(':'))), p, q, x, y);
      }
    }
    return null;
  }

  /** Checks every stop of the input. */
  static Outcome check(Consumer<Detector.Builder> config, List<Expression> watched, List<Op> ops) {
    Original o = run(config, watched, ops);
    int stops = 0, checked = 0, skipped = 0, empty = 0, teeth = 0, pinned = 0;
    for (int stop = 0; stop <= ops.size(); stop++) {
      stops++;
      OptionalLong point = o.points().get(stop);
      if (point.isEmpty()) {
        empty++;
        continue;
      }
      if (o.dropped().get(stop) > 0) {
        skipped++;
        continue;
      }
      long from = point.getAsLong();
      checked++;
      Difference d = replay(o, stop, from);
      if (d != null) return new Outcome(Stats.NONE, new Failure(stop, from, d));
      if (from <= firstTimestamp(o)) pinned++;
      long next = nextSampleAfter(o, stop, from);
      if (next != Long.MIN_VALUE && replay(o, stop, next) != null) teeth++;
    }
    return new Outcome(new Stats(stops, checked, skipped, empty, teeth, pinned), null);
  }

  /** The timestamp of the first accepted Sample or advance. */
  private static long firstTimestamp(Original o) {
    for (int i = 0; i < o.ops().size(); i++) {
      Op op = o.ops().get(i);
      if (o.accepted().get(i + 1) && op.kind() != Op.Kind.CLOSE) return op.timestamp();
    }
    return Long.MAX_VALUE;
  }

  /** The timestamp of the first accepted Sample after {@code from} up to the stop, or MIN_VALUE. */
  private static long nextSampleAfter(Original o, int stop, long from) {
    long next = Long.MAX_VALUE;
    for (int i = 0; i < stop; i++) {
      Op op = o.ops().get(i);
      if (o.accepted().get(i + 1) && op.kind() == Op.Kind.PUSH && op.timestamp() > from) {
        next = Math.min(next, op.timestamp());
      }
    }
    return next == Long.MAX_VALUE ? Long.MIN_VALUE : next;
  }
}
