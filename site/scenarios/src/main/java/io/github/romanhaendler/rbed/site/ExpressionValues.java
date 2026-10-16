package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Condition;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.EventListener;
import io.github.romanhaendler.rbed.Expression;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The value of an Expression at every Sample, as rbed computes it. rbed's public API has no way to
 * read an Expression, only to compare it, so the value is found by comparison: a Rule compares
 * the Expression with a probe Channel whose value differs per Sample, and a bisection over the
 * ordered doubles narrows each Sample's probe down to the exact value. No arithmetic of the
 * Expression is repeated here, so the site cannot drift from the library.
 *
 * <p>ponytail: about 64 Detector runs per call; fine for the site's few hundred Samples, a
 * K-ary search with K probe Channels per run would cut that if Scenarios grow.
 */
final class ExpressionValues {
  private static final Channel PROBE = Channel.named("probe of the plotted Expression");

  private ExpressionValues() {}

  /** One value per Sample, NaN where the Expression is undefined. */
  static double[] of(Expression expression, List<Sample> samples) {
    int n = samples.size();
    double[] probes = new double[n];
    boolean[] defined =
        holds(expression.between(-Double.MAX_VALUE, Double.MAX_VALUE), samples, probes);
    long[] low = new long[n];
    long[] high = new long[n];
    Arrays.fill(low, key(-Double.MAX_VALUE));
    Arrays.fill(high, key(Double.MAX_VALUE));
    // Invariant: the value lies in [low, high] in key order; the probe is the middle.
    while (true) {
      boolean open = false;
      for (int i = 0; i < n; i++) {
        open |= defined[i] && low[i] != high[i];
        probes[i] = value(low[i] + ((high[i] - low[i]) >>> 1));
      }
      if (!open) break;
      boolean[] greater = holds(expression.above(PROBE), samples, probes);
      for (int i = 0; i < n; i++) {
        long middle = low[i] + ((high[i] - low[i]) >>> 1);
        if (greater[i]) low[i] = middle + 1;
        else high[i] = middle;
      }
    }
    double[] values = new double[n];
    for (int i = 0; i < n; i++) values[i] = defined[i] ? value(low[i]) + 0.0 : Double.NaN;
    return values;
  }

  /** Whether the Condition holds at each Sample, with the probe Channel set per Sample. */
  private static boolean[] holds(Condition condition, List<Sample> samples, double[] probes) {
    boolean[] holds = new boolean[samples.size()];
    boolean[] running = new boolean[1];
    Detector detector = Detector.builder()
        .rules(Rule.named("Holds").when(condition))
        .listener(new EventListener() {
          @Override public void onConfirmed(Event e) {
            running[0] = true;
          }

          @Override public void onEnded(Event e) {
            running[0] = false;
          }
        })
        .build();
    for (int i = 0; i < holds.length; i++) {
      Sample sample = samples.get(i);
      Map<Channel, Double> values = new HashMap<>(sample.values());
      values.put(PROBE, probes[i]);
      detector.push(sample.timestamp(), values);
      holds[i] = running[0];
    }
    return holds;
  }

  /** A long whose order is the order of the doubles (for all but NaN). */
  private static long key(double value) {
    long bits = Double.doubleToLongBits(value);
    return bits >= 0 ? bits : bits ^ Long.MAX_VALUE;
  }

  private static double value(long key) {
    return Double.longBitsToDouble(key >= 0 ? key : key ^ Long.MAX_VALUE);
  }
}
