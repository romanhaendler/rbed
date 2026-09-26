package rbed;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Running per-Channel statistics over the Samples of one Event, for the Channels its Rule
 * aggregates. Indexed like the Rule's aggregated Channels, so adding a Sample neither looks
 * anything up nor allocates. This is the innermost loop of the library (running Events x
 * aggregated Channels per Sample), hence plain arrays.
 */
final class Accumulator {
  private double[] min = new double[0];
  private double[] max = new double[0];
  private double[] sum = new double[0];
  private long[] count = new long[0];
  /** Sum of value x held milliseconds up to {@link #lastTime}. */
  private double[] integral = new double[0];
  /** The value holding since {@link #lastTime}; NaN if none. */
  private double[] lastValue = new double[0];
  private long[] firstTime = new long[0];
  private long lastTime;
  private long startTime;
  private boolean empty = true;

  /** Adds the Sample at {@code time}: the current value of each of the first {@code n} Channels. */
  void add(ChannelState[] channels, int n, long time) {
    if (n > count.length) grow(n);
    if (empty) {
      empty = false;
      startTime = time;
    }
    long held = time - lastTime;
    for (int i = 0; i < n; i++) {
      double previous = lastValue[i];
      if (previous == previous) integral[i] += previous * held; // not NaN
      double v = channels[i].value;
      lastValue[i] = v;
      if (v == v) {
        if (count[i] == 0) firstTime[i] = time;
        if (v < min[i]) min[i] = v;
        if (v > max[i]) max[i] = v;
        sum[i] += v;
        count[i]++;
      }
    }
    lastTime = time;
  }

  /** Appends the Samples of {@code later}, which followed this one's without a gap. */
  void addAll(Accumulator later) {
    if (later.empty) return;
    if (later.count.length > count.length) grow(later.count.length);
    long held = later.startTime - lastTime;
    for (int i = 0; i < later.count.length; i++) {
      double previous = lastValue[i];
      if (!empty && previous == previous) integral[i] += previous * held;
      if (count[i] == 0) firstTime[i] = later.firstTime[i];
      min[i] = Math.min(min[i], later.min[i]);
      max[i] = Math.max(max[i], later.max[i]);
      sum[i] += later.sum[i];
      count[i] += later.count[i];
      integral[i] += later.integral[i];
      lastValue[i] = later.lastValue[i];
    }
    if (empty) startTime = later.startTime;
    empty = false;
    lastTime = later.lastTime;
  }

  void clear() {
    Arrays.fill(min, Double.POSITIVE_INFINITY);
    Arrays.fill(max, Double.NEGATIVE_INFINITY);
    Arrays.fill(sum, 0);
    Arrays.fill(count, 0);
    Arrays.fill(integral, 0);
    Arrays.fill(lastValue, Double.NaN);
    empty = true;
  }

  /**
   * Aggregates of the first {@code n} Channels, including those without values (count 0), with
   * the last values held until {@code until}.
   */
  Map<String, Aggregates> snapshot(ChannelState[] channels, int n, long until) {
    Map<String, Aggregates> out = new LinkedHashMap<>();
    for (int i = 0; i < n; i++) {
      out.put(channels[i].name, i < count.length && count[i] > 0 ? aggregates(i, until)
          : Aggregates.EMPTY);
    }
    return Map.copyOf(out);
  }

  private Aggregates aggregates(int i, long until) {
    double last = lastValue[i];
    double valueMillis = integral[i] + (last == last ? last * (until - lastTime) : 0);
    long span = until - firstTime[i];
    return new Aggregates(min[i], max[i], sum[i] / count[i], count[i],
        span > 0 ? valueMillis / span : Double.NaN, valueMillis / 1000);
  }

  private void grow(int n) {
    int old = count.length;
    min = Arrays.copyOf(min, n);
    max = Arrays.copyOf(max, n);
    sum = Arrays.copyOf(sum, n);
    count = Arrays.copyOf(count, n);
    integral = Arrays.copyOf(integral, n);
    lastValue = Arrays.copyOf(lastValue, n);
    firstTime = Arrays.copyOf(firstTime, n);
    Arrays.fill(min, old, n, Double.POSITIVE_INFINITY);
    Arrays.fill(max, old, n, Double.NEGATIVE_INFINITY);
    Arrays.fill(lastValue, old, n, Double.NaN);
  }
}
