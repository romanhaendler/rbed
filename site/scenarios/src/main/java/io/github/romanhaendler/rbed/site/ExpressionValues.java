package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.Expression;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The value of an Expression at every moment of a Scenario's own run, as rbed computes it: at
 * each Sample the Scenario pushes and each time it advances, through its own {@code detect} (so a
 * sampling rate, time advances or a restart are followed). rbed's public API has no way to read
 * an Expression, only to compare it, so the value is found by comparison: Rules compare the
 * Expression with probe Channels whose values differ per Sample, and a search over the ordered
 * doubles narrows each moment down to the exact value. No arithmetic of the Expression is
 * repeated here, so the site cannot drift from the library.
 *
 * <p>An advance carries no Sample, so the probes keep the values of the Sample before it: the
 * k-th moment after a Sample (the Sample itself is the 0th) has probe Channels of its own.
 *
 * <p>Each probing Rule costs about as much per Sample as the rest of a run, so the number of
 * comparisons is what counts, and a comparison tells one bit at most. The search therefore
 * bisects, but on bit boundaries, and every few runs also tests whether the value is the end of
 * its interval: a value with a short binary form (a count, whole seconds or minutes) is found
 * after its significant bits instead of all 64. Measured in Node at the default parameters: the
 * count on its page in 3.7 ms instead of 11.3 ms, the total duration in 4.6 ms instead of 10.2
 * ms; a moving average, whose values have full mantissas, in 4.7 ms instead of 4.4 ms.
 */
final class ExpressionValues {
  /** Every so many runs also test for the end of the interval (tuned by measuring 2, 4 and 8). */
  static final int TEST_EVERY = 8;

  private ExpressionValues() {}

  /** The values at the Samples (in order) and at the advances (in order); NaN where undefined. */
  record Values(double[] atSamples, double[] atAdvances) {}

  /**
   * The Expression's values in the Scenario's run with these parameter values over
   * {@code samples}. The Scenario's Rules come first, so the Expression may refer to them
   * ({@code count(run)}).
   */
  static Values of(Scenario scenario, Expression expression, Map<String, Double> values,
      List<Sample> samples) {
    Rule[] rules = scenario.rules(values);
    // First run: the moments, and where the Expression is defined (at every position alike).
    Rule definedRule = Rule.named("probe: defined")
        .when(expression.between(-Double.MAX_VALUE, Double.MAX_VALUE));
    Probe first = run(scenario, values, rules, new Rule[][] {{definedRule}}, samples);
    int n = first.positions.size();
    // An advance before the first Sample would have no probe values to compare with.
    if (n > 0 && first.positions.get(0) != 0) {
      throw new IllegalStateException("Scenario " + scenario.id
          + " advances time before its first Sample");
    }
    int positions = 1;
    for (int p : first.positions) positions = Math.max(positions, p + 1);
    // The moment each Sample is pushed at.
    int[] pushed = new int[samples.size()];
    int count = 0;
    for (int m = 0; m < n; m++) {
      if (first.positions.get(m) > 0) continue;
      if (count == samples.size()) break;
      pushed[count++] = m;
    }
    if (count != samples.size() || n - count != first.advances.size()) {
      throw new IllegalStateException("Scenario " + scenario.id + " does not push each Sample once");
    }
    // Per position, a Rule comparing with a bisecting probe, and one testing for the value
    // the search has narrowed down to (see below).
    Channel[] bisecting = new Channel[positions];
    Channel[] testing = new Channel[positions];
    Rule[][] bisect = new Rule[positions][];
    Rule[][] bisectAndTest = new Rule[positions][];
    for (int p = 0; p < positions; p++) {
      bisecting[p] = Channel.named("probe " + p);
      testing[p] = Channel.named("probe " + p + " end");
      Rule above = Rule.named("probe: above " + p).when(expression.above(bisecting[p]));
      bisect[p] = new Rule[] {above};
      bisectAndTest[p] = new Rule[] {above,
          Rule.named("probe: above end " + p).when(expression.above(testing[p]))};
    }
    // Invariant: the value lies in [low, high] in the unsigned order of order(). The search
    // starts from all longs, so it halves on bit boundaries and meets a value with a short
    // binary form (an integer, a half) early; every TEST_EVERY-th run then also tests whether
    // the value is the end of its interval it has reached (low for a positive value, high for
    // a negative one, whose order has trailing ones), which ends the search for that moment.
    long[] low = new long[n];
    long[] high = new long[n];
    Arrays.fill(high, -1L);
    boolean[] defined = new boolean[n];
    int[] position = new int[n];
    for (int m = 0; m < n; m++) {
      defined[m] = first.holds.get(m)[0];
      position[m] = first.positions.get(m);
    }
    // The Samples with every probe Channel; each run sets the probes' values in place.
    List<Map<Channel, Double>> probedValues = new ArrayList<>();
    List<Sample> probed = new ArrayList<>();
    for (Sample sample : samples) {
      Map<Channel, Double> sampleValues = new HashMap<>(sample.values());
      for (int p = 0; p < positions; p++) {
        sampleValues.put(bisecting[p], 0.0);
        sampleValues.put(testing[p], 0.0);
      }
      probedValues.add(sampleValues);
      probed.add(new Sample(sample.timestamp(), sampleValues));
    }
    // Longs are slow in the compiled JavaScript, so each moment's probes are worked out once.
    long[] middle = new long[n];
    double[] middleValue = new double[n];
    double[] endValue = new double[n];
    boolean[] open = new boolean[n];
    for (int round = 1; ; round++) {
      boolean test = round % TEST_EVERY == 0;
      boolean any = false;
      for (int m = 0; m < n; m++) {
        open[m] = defined[m] && low[m] != high[m];
        if (!open[m]) continue;
        any = true;
        middle[m] = low[m] + ((high[m] - low[m]) >>> 1);
        middleValue[m] = value(middle[m]);
        // the end of the interval: low for a positive value, high for a negative one
        if (test) endValue[m] = low[m] < 0 ? value(low[m]) : high[m] >= 0 ? value(high[m] - 1) : 0;
      }
      if (!any) break;
      for (int p = 0; p < positions; p++) {
        for (int i = 0; i < samples.size(); i++) {
          int m = pushed[i] + p;
          if (m < n && position[m] == p && open[m]) {
            probedValues.get(i).put(bisecting[p], finite(middleValue[m]));
            if (test) probedValues.get(i).put(testing[p], finite(endValue[m]));
          }
        }
      }
      Probe probe = run(scenario, values, rules, test ? bisectAndTest : bisect, probed);
      for (int m = 0; m < n; m++) {
        if (!open[m]) continue;
        boolean[] holds = probe.holds.get(m);
        boolean positive = low[m] < 0;
        boolean negative = high[m] >= 0;
        long lowBefore = low[m];
        long highBefore = high[m];
        if (above(middleValue[m], middle[m], holds[0])) low[m] = middle[m] + 1;
        else high[m] = middle[m];
        if (test && positive && !above(endValue[m], lowBefore, holds[1])) {
          low[m] = high[m] = lowBefore;
        } else if (test && negative && above(endValue[m], highBefore - 1, holds[1])) {
          low[m] = high[m] = highBefore;
        }
      }
    }
    double[] atSamples = new double[samples.size()];
    double[] atAdvances = new double[n - samples.size()];
    for (int m = 0, i = 0, a = 0; m < n; m++) {
      double v = defined[m] ? value(low[m]) + 0.0 : Double.NaN;
      if (position[m] == 0) atSamples[i++] = v;
      else atAdvances[a++] = v;
    }
    return new Values(atSamples, atAdvances);
  }

  /**
   * Whether the defined (so finite) value is above {@code probe}, the double of order {@code u}:
   * known without probing where {@code probe} is not finite, since a Detector takes only finite
   * values; else what the probe found.
   */
  private static boolean above(double probe, long u, boolean probed) {
    if (Double.isFinite(probe)) return probed;
    return u >= 0;  // an infinity or NaN below the negative doubles' order, or above the positive's
  }

  /** A probe Channel's value: the double, or 0 where it is not finite and is not probed. */
  private static double finite(double probe) {
    return Double.isFinite(probe) ? probe : 0;
  }

  /**
   * Runs the Scenario with the probing Rules after its own over the Samples, and records at each moment which of the Rules of its position hold. With a single group,
   * that group is read at every position.
   */
  private static Probe run(Scenario scenario, Map<String, Double> values, Rule[] rules,
      Rule[][] byPosition, List<Sample> samples) {
    List<Rule> all = new ArrayList<>(Arrays.asList(rules));
    for (Rule[] group : byPosition) all.addAll(Arrays.asList(group));
    Probe probe = new Probe(byPosition);
    scenario.detect(all.toArray(Rule[]::new), samples, values, probe);
    return probe;
  }


  /** The bridge's Lifecycle, also noting after every moment which probing Rules hold. */
  private static final class Probe extends Lifecycle {
    final Rule[][] byPosition;
    final Set<String> running = new HashSet<>();
    final List<Integer> positions = new ArrayList<>();
    final List<boolean[]> holds = new ArrayList<>();
    int position;

    Probe(Rule[][] byPosition) {
      this.byPosition = byPosition;
    }

    @Override
    void push(Detector detector, Sample sample) {
      super.push(detector, sample);
      moment(0);
    }

    @Override
    void pushValues(Detector detector, Sample sample) {
      super.pushValues(detector, sample);
      moment(0);
    }

    @Override
    void advance(Detector detector, long timestamp) {
      super.advance(detector, timestamp);
      moment(position + 1);
    }

    private void moment(int p) {
      position = p;
      Rule[] group = byPosition[Math.min(p, byPosition.length - 1)];
      boolean[] state = new boolean[group.length];
      for (int j = 0; j < group.length; j++) state[j] = running.contains(group[j].name());
      positions.add(p);
      holds.add(state);
    }

    @Override
    public void onConfirmed(Event event) {
      running.add(event.name());
    }

    @Override
    public void onEnded(Event event) {
      super.onEnded(event);
      running.remove(event.name());
    }
  }

  /** A long whose unsigned order is the order of the doubles (for all but NaN). */
  private static long order(double value) {
    long bits = Double.doubleToLongBits(value);
    return bits >= 0 ? bits ^ Long.MIN_VALUE : ~bits;
  }

  /** The double of an order(); NaN for a long no double has. */
  private static double value(long order) {
    return Double.longBitsToDouble(order < 0 ? order ^ Long.MIN_VALUE : ~order);
  }
}
