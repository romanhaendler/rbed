package io.github.romanhaendler.rbed;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Running Aggregates of one Event, for the Channels its Rule aggregates. Indexed like {@link
 * AggregatedChannels}, so recording a Sample neither looks anything up nor allocates. This is the
 * innermost loop of the library (running Events x aggregated Channels per Sample), hence plain
 * arrays.
 */
final class Accumulator {
  /** The Aggregates a Condition can read from another Rule's latest Event. */
  enum Aggregate { MIN, MAX, MEAN }

  private double[] min = new double[0];
  private double[] max = new double[0];
  private double[] sum = new double[0];
  private long[] count = new long[0];
  /** Sum of value x held milliseconds up to {@link #lastTime}. */
  private double[] integral = new double[0];
  /** The value holding since {@link #lastTime}; NaN if none. */
  private double[] lastValue = new double[0];
  /** Whether the Channel had a value during the Event, and since when. */
  private boolean[] valued = new boolean[0];
  private long[] firstTime = new long[0];
  private long lastTime;
  private long startTime;
  private boolean empty = true;

  /** Records the Sample at {@code time}: the current value of each aggregated Channel. */
  void add(AggregatedChannels channels, long time) {
    int n = channels.count();
    if (n > count.length) grow(n);
    begin(time);
    long held = time - lastTime;
    for (int i = 0; i < n; i++) {
      double previous = lastValue[i];
      if (previous == previous) integral[i] += previous * held; // not NaN
      double v = channels.get(i).value;
      lastValue[i] = v;
      if (v == v) {
        markValued(i, time);
        if (v < min[i]) min[i] = v;
        if (v > max[i]) max[i] = v;
        sum[i] += v;
        count[i]++;
      }
    }
    lastTime = time;
  }

  /**
   * Starts at a time advance: the values the Channels hold count for the time-weighted
   * Aggregates from {@code time} on, but not as a Sample.
   */
  void hold(AggregatedChannels channels, long time) {
    int n = channels.count();
    if (n > count.length) grow(n);
    begin(time);
    for (int i = 0; i < n; i++) {
      double v = channels.get(i).value;
      lastValue[i] = v;
      if (v == v) markValued(i, time);
    }
    lastTime = time;
  }

  /** Appends the moments recorded by {@code later}, which followed this one's without a gap. */
  void addAll(Accumulator later) {
    if (later.empty) return;
    if (later.count.length > count.length) grow(later.count.length);
    long held = later.startTime - lastTime;
    for (int i = 0; i < later.count.length; i++) {
      double previous = lastValue[i];
      if (!empty && previous == previous) integral[i] += previous * held;
      if (later.valued[i]) markValued(i, later.firstTime[i]);
      min[i] = Math.min(min[i], later.min[i]);
      max[i] = Math.max(max[i], later.max[i]);
      sum[i] += later.sum[i];
      count[i] += later.count[i];
      integral[i] += later.integral[i];
      lastValue[i] = later.lastValue[i];
    }
    begin(later.startTime);
    lastTime = later.lastTime;
  }

  void save(StateWriter w) {
    w.float64s(min);
    w.float64s(max);
    w.float64s(sum);
    w.int64s(count);
    w.float64s(integral);
    w.float64s(lastValue);
    w.bools(valued);
    w.int64s(firstTime);
    w.int64(lastTime);
    w.int64(startTime);
    w.bool(empty);
  }

  void load(StateReader r) throws IOException {
    min = r.float64s();
    max = r.float64s();
    sum = r.float64s();
    count = r.int64s();
    integral = r.float64s();
    lastValue = r.float64s();
    valued = r.bools();
    firstTime = r.int64s();
    lastTime = r.int64();
    startTime = r.int64();
    empty = r.bool();
    int n = count.length;
    if (min.length != n || max.length != n || sum.length != n || integral.length != n
        || lastValue.length != n || valued.length != n || firstTime.length != n) {
      throw new IOException("inconsistent Aggregates");
    }
  }

  void clear() {
    Arrays.fill(min, Double.POSITIVE_INFINITY);
    Arrays.fill(max, Double.NEGATIVE_INFINITY);
    Arrays.fill(sum, 0);
    Arrays.fill(count, 0);
    Arrays.fill(integral, 0);
    Arrays.fill(lastValue, Double.NaN);
    Arrays.fill(valued, false);
    empty = true;
  }

  /** A Sample-based Aggregate of Channel {@code i}; NaN if it had no value in a Sample. */
  double aggregate(int i, Aggregate aggregate) {
    if (i >= count.length || count[i] == 0) return Double.NaN;
    return switch (aggregate) {
      case MIN -> min[i];
      case MAX -> max[i];
      case MEAN -> sum[i] / count[i];
    };
  }

  /** The Aggregates of every aggregated Channel, with values held until {@code until}. */
  Map<Channel, Aggregates> snapshot(AggregatedChannels channels, long until) {
    Map<Channel, Aggregates> out = new LinkedHashMap<>();
    for (int i = 0; i < channels.count(); i++) {
      boolean hasValue = i < valued.length && valued[i];
      out.put(channels.get(i).channel, hasValue ? aggregates(i, until) : Aggregates.EMPTY);
    }
    return Map.copyOf(out);
  }

  private Aggregates aggregates(int i, long until) {
    double last = lastValue[i];
    double valueMillis = integral[i] + (last == last ? last * (until - lastTime) : 0);
    long span = until - firstTime[i];
    boolean sampled = count[i] > 0;
    return new Aggregates(
        sampled ? min[i] : Double.NaN,
        sampled ? max[i] : Double.NaN,
        sampled ? sum[i] / count[i] : Double.NaN,
        count[i],
        span > 0 ? valueMillis / span : Double.NaN,
        valueMillis / 1000);
  }

  private void begin(long time) {
    if (!empty) return;
    empty = false;
    startTime = time;
  }

  private void markValued(int i, long time) {
    if (valued[i]) return;
    valued[i] = true;
    firstTime[i] = time;
  }

  private void grow(int n) {
    int old = count.length;
    min = Arrays.copyOf(min, n);
    max = Arrays.copyOf(max, n);
    sum = Arrays.copyOf(sum, n);
    count = Arrays.copyOf(count, n);
    integral = Arrays.copyOf(integral, n);
    lastValue = Arrays.copyOf(lastValue, n);
    valued = Arrays.copyOf(valued, n);
    firstTime = Arrays.copyOf(firstTime, n);
    Arrays.fill(min, old, n, Double.POSITIVE_INFINITY);
    Arrays.fill(max, old, n, Double.NEGATIVE_INFINITY);
    Arrays.fill(lastValue, old, n, Double.NaN);
  }
}
