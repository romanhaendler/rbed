package rbed;

import java.util.LinkedHashMap;
import java.util.Map;

/** Running per-Channel min/max/sum/count with constant memory per Channel. */
final class Accumulator {
  private final Map<String, double[]> stats = new LinkedHashMap<>(); // min, max, sum, count

  void add(Map<String, Double> values) {
    values.forEach((channel, v) -> {
      double[] s = stats.get(channel);
      if (s == null) {
        stats.put(channel, new double[] {v, v, v, 1});
      } else {
        s[0] = Math.min(s[0], v);
        s[1] = Math.max(s[1], v);
        s[2] += v;
        s[3]++;
      }
    });
  }

  void addAll(Accumulator other) {
    other.stats.forEach((channel, o) -> {
      double[] s = stats.get(channel);
      if (s == null) {
        stats.put(channel, o.clone());
      } else {
        s[0] = Math.min(s[0], o[0]);
        s[1] = Math.max(s[1], o[1]);
        s[2] += o[2];
        s[3] += o[3];
      }
    });
  }

  Map<String, Aggregates> snapshot() {
    Map<String, Aggregates> out = new LinkedHashMap<>();
    stats.forEach((c, s) -> out.put(c, new Aggregates(s[0], s[1], s[2] / s[3], (long) s[3])));
    return Map.copyOf(out);
  }
}
