package rbed;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Running per-Channel minimum, maximum, sum and count over the Samples of one Event. Indexed like
 * the Detector's Channels, so adding a Sample neither looks anything up nor allocates. This is the
 * innermost loop of the library (Confirmed Events x Channels per Sample), hence plain arrays.
 */
final class Accumulator {
  private double[] min = new double[0];
  private double[] max = new double[0];
  private double[] sum = new double[0];
  private long[] count = new long[0];

  /** Adds the current value of each of the first {@code n} Channels that has one. */
  void add(ChannelState[] channels, int n) {
    if (n > count.length) grow(n);
    for (int i = 0; i < n; i++) {
      double v = channels[i].value;
      if (v == v) { // not NaN, i.e. the Channel has a value
        if (v < min[i]) min[i] = v;
        if (v > max[i]) max[i] = v;
        sum[i] += v;
        count[i]++;
      }
    }
  }

  void addAll(Accumulator other) {
    if (other.count.length > count.length) grow(other.count.length);
    for (int i = 0; i < other.count.length; i++) {
      min[i] = Math.min(min[i], other.min[i]);
      max[i] = Math.max(max[i], other.max[i]);
      sum[i] += other.sum[i];
      count[i] += other.count[i];
    }
  }

  void clear() {
    Arrays.fill(min, Double.POSITIVE_INFINITY);
    Arrays.fill(max, Double.NEGATIVE_INFINITY);
    Arrays.fill(sum, 0);
    Arrays.fill(count, 0);
  }

  Map<String, Aggregates> snapshot(ChannelState[] channels) {
    Map<String, Aggregates> out = new LinkedHashMap<>();
    for (int i = 0; i < count.length; i++) {
      if (count[i] > 0) {
        out.put(channels[i].name, new Aggregates(min[i], max[i], sum[i] / count[i], count[i]));
      }
    }
    return Map.copyOf(out);
  }

  private void grow(int n) {
    int old = count.length;
    min = Arrays.copyOf(min, n);
    max = Arrays.copyOf(max, n);
    sum = Arrays.copyOf(sum, n);
    count = Arrays.copyOf(count, n);
    Arrays.fill(min, old, n, Double.POSITIVE_INFINITY);
    Arrays.fill(max, old, n, Double.NEGATIVE_INFINITY);
  }
}
