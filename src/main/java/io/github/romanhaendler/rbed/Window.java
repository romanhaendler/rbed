package io.github.romanhaendler.rbed;

/**
 * The recent values of one Channel for a moving average. Memory is bounded by the window size and
 * nothing is allocated per value once the window is full. The sum is exact (see {@link
 * ExactSum}), so the average depends only on the values in the window, never on the order they
 * arrived in or on values that left.
 */
abstract class Window {
  final Detector detector;

  Window(Detector detector) {
    this.detector = detector;
  }

  /** @param need the earliest timestamp a replay must start at to reproduce this entry */
  abstract void add(long time, double value, long need);

  /**
   * The earliest timestamp a replay must start at to reproduce this window: that of its oldest
   * entry (the entries after it are not older); NONE if empty.
   */
  abstract long need();

  /** The mean of the window, or NaN while it is warming up. */
  abstract double average();

  abstract void save(StateWriter w);

  abstract void load(StateReader r) throws java.io.IOException;

  /** The last {@code n} values. */
  static final class OverSamples extends Window {
    private final double[] values;
    private final long[] needs;
    private int size;
    private int next;
    private final ExactSum sum = new ExactSum();

    OverSamples(Detector detector, int n) {
      super(detector);
      detector.windowSamples(n);
      values = new double[n];
      needs = new long[n];
    }

    @Override
    long need() {
      return size == 0 ? ValueTrack.NONE : needs[size == values.length ? next : 0];
    }

    @Override
    void add(long time, double value, long need) {
      if (size == values.length) {
        sum.remove(values[next]);
      } else {
        size++;
        detector.retain();
      }
      values[next] = value;
      needs[next] = need;
      sum.add(value);
      next = (next + 1) % values.length;
    }

    @Override
    double average() {
      return size == values.length ? sum.value() / size : Double.NaN;
    }

    @Override
    void save(StateWriter w) {
      w.float64s(values);
      w.int64s(needs);
      w.int32(size);
      w.int32(next);
      sum.save(w);
    }

    @Override
    void load(StateReader r) throws java.io.IOException {
      double[] loaded = r.float64s();
      if (loaded.length != values.length) throw new java.io.IOException("window size");
      System.arraycopy(loaded, 0, values, 0, loaded.length);
      long[] loadedNeeds = r.int64s();
      if (loadedNeeds.length != needs.length) throw new java.io.IOException("window size");
      System.arraycopy(loadedNeeds, 0, needs, 0, loadedNeeds.length);
      size = r.int32();
      next = r.int32();
      sum.load(r);
      if (size < 0 || size > values.length || next < 0 || next >= values.length) {
        throw new java.io.IOException("window position");
      }
    }
  }

  /** The values from the last {@code millis}, counted back from the latest value. */
  static final class OverDuration extends Window {
    private final long millis;
    private final String binding;
    private long[] times;
    private double[] values;
    private long[] needs;
    /** The need of the latest entry that left by time: it proves the window is warm. */
    private long expiredNeed = ValueTrack.NONE;
    private int head;
    private int size;
    private final ExactSum sum = new ExactSum();
    private boolean started;
    private long first;

    OverDuration(Detector detector, long millis) {
      super(detector);
      detector.windowMillis(millis);
      this.millis = millis;
      binding = detector.binding;
      int initial = Math.min(16, detector.retentionLimit());
      times = new long[initial];
      values = new double[initial];
      needs = new long[initial];
    }

    @Override
    long need() {
      return Math.min(size == 0 ? ValueTrack.NONE : needs[head], expiredNeed);
    }

    @Override
    void add(long time, double value, long need) {
      if (!started) {
        started = true;
        first = time;
      }
      // evict by time first: an entry leaving by time is not a drop
      while (size > 0 && time - times[head] > millis) {
        expiredNeed = needs[head];
        sum.remove(values[head]);
        head = (head + 1) % times.length;
        size--;
        detector.release();
      }
      if (size == detector.retentionLimit()) { // still full: drop the oldest like one leaving by time
        sum.remove(values[head]);
        head = (head + 1) % times.length;
        size--;
        detector.release();
        detector.recordDrop(binding);
      }
      if (size == times.length) grow();
      int tail = (head + size) % times.length;
      times[tail] = time;
      values[tail] = value;
      needs[tail] = need;
      size++;
      detector.retain();
      sum.add(value);
      while (time - times[head] > millis) {
        expiredNeed = needs[head];
        sum.remove(values[head]);
        head = (head + 1) % times.length;
        size--;
        detector.release();
      }
    }

    /** Writes the entries oldest first; the ring's position and capacity are not state. */
    @Override
    void save(StateWriter w) {
      w.int32(size);
      for (int i = 0; i < size; i++) {
        w.int64(times[(head + i) % times.length]);
        w.float64(values[(head + i) % values.length]);
        w.int64(needs[(head + i) % needs.length]);
      }
      w.int64(expiredNeed);
      sum.save(w);
      w.bool(started);
      w.int64(first);
    }

    @Override
    void load(StateReader r) throws java.io.IOException {
      int n = r.length();
      if (n > detector.retentionLimit()) throw new java.io.IOException("window size");
      times = new long[Math.max(times.length, n)];
      values = new double[times.length];
      needs = new long[times.length];
      for (int i = 0; i < n; i++) {
        times[i] = r.int64();
        values[i] = r.float64();
        needs[i] = r.int64();
      }
      expiredNeed = r.int64();
      size = n; // a restore only happens in a Detector just built, so head is 0
      sum.load(r);
      started = r.bool();
      first = r.int64();
    }

    @Override
    double average() {
      boolean warm = started && latest() - first >= millis;
      return warm ? sum.value() / size : Double.NaN;
    }

    private long latest() {
      return times[(head + size - 1) % times.length];
    }

    private void grow() {
      long[] newTimes = new long[Math.min(times.length * 2, detector.retentionLimit())];
      double[] newValues = new double[Math.min(values.length * 2, detector.retentionLimit())];
      long[] newNeeds = new long[newValues.length];
      for (int i = 0; i < size; i++) {
        newTimes[i] = times[(head + i) % times.length];
        newValues[i] = values[(head + i) % values.length];
        newNeeds[i] = needs[(head + i) % needs.length];
      }
      times = newTimes;
      values = newValues;
      needs = newNeeds;
      head = 0;
    }
  }
}
