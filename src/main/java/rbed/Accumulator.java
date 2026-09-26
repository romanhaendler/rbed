package rbed;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Running per-Channel minimum, maximum, sum and count over the Samples of one Event. Indexed like
 * the Detector's Channel list, so adding a Sample neither looks anything up nor allocates.
 */
final class Accumulator {
  private double[] min = new double[0];
  private double[] max = new double[0];
  private double[] sum = new double[0];
  private long[] count = new long[0];

  /** Adds the current value of every Channel that has one. */
  void add(List<ChannelState> channels) {
    ensureCapacity(channels.size());
    for (int i = 0; i < channels.size(); i++) {
      double v = channels.get(i).value;
      if (!Double.isNaN(v)) include(i, v, v, v, 1);
    }
  }

  void addAll(Accumulator other) {
    ensureCapacity(other.count.length);
    for (int i = 0; i < other.count.length; i++) {
      if (other.count[i] > 0) include(i, other.min[i], other.max[i], other.sum[i], other.count[i]);
    }
  }

  void clear() {
    Arrays.fill(sum, 0);
    Arrays.fill(count, 0);
  }

  Map<String, Aggregates> snapshot(List<ChannelState> channels) {
    Map<String, Aggregates> out = new LinkedHashMap<>();
    for (int i = 0; i < count.length; i++) {
      if (count[i] > 0) {
        out.put(channels.get(i).name, new Aggregates(min[i], max[i], sum[i] / count[i], count[i]));
      }
    }
    return Map.copyOf(out);
  }

  private void include(int i, double lowest, double highest, double total, long n) {
    min[i] = count[i] == 0 ? lowest : Math.min(min[i], lowest);
    max[i] = count[i] == 0 ? highest : Math.max(max[i], highest);
    sum[i] += total;
    count[i] += n;
  }

  private void ensureCapacity(int channels) {
    if (channels <= count.length) return;
    min = Arrays.copyOf(min, channels);
    max = Arrays.copyOf(max, channels);
    sum = Arrays.copyOf(sum, channels);
    count = Arrays.copyOf(count, channels);
  }
}
