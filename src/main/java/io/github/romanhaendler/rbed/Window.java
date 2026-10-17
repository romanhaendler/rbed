package io.github.romanhaendler.rbed;

/**
 * The recent values of one Channel for a moving average. Memory is bounded by the window size and
 * nothing is allocated per value once the window is full. The running sum is compensated, so a
 * large value leaving the window does not wipe out small ones, and it is recomputed from the
 * window each time the window has turned over, so rounding error cannot build up on endless
 * streams. Both keep the amortized cost per value constant.
 */
abstract class Window {
  final Detector detector;

  Window(Detector detector) {
    this.detector = detector;
  }

  /**
   * Adds a value; {@code need} is the earliest timestamp a replay must start at to reproduce it
   * (see {@link Detector#replayPoint}).
   */
  abstract void add(long time, double value, long need);

  /** The earliest timestamp a replay must start at to reproduce this window; NONE if empty. */
  abstract long need();

  /** The value added in this moment also needs the moment's replay start, {@code closed}. */
  abstract void settleNewest(long closed);

  /** The mean of the window, or NaN while it is warming up. */
  abstract double average();

  /** The last {@code n} values. */
  static final class OverSamples extends Window {
    private final double[] values;
    private final long[] needs;
    private int size;
    private int next;
    private int evictedSinceExactSum;
    private final CompensatedSum sum = new CompensatedSum();

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
    void settleNewest(long closed) {
      int newest = (next + values.length - 1) % values.length;
      needs[newest] = Math.min(needs[newest], closed);
    }

    @Override
    void add(long time, double value, long need) {
      if (size == values.length) {
        sum.add(-values[next]);
        evictedSinceExactSum++;
      } else {
        size++;
        detector.retain();
      }
      values[next] = value;
      needs[next] = need;
      sum.add(value);
      next = (next + 1) % values.length;
      if (evictedSinceExactSum == values.length) {
        evictedSinceExactSum = 0;
        sum.reset();
        for (double v : values) sum.add(v);
      }
    }

    @Override
    double average() {
      return size == values.length ? sum.value() / size : Double.NaN;
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
    private int evictedSinceExactSum;
    private final CompensatedSum sum = new CompensatedSum();
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
    void settleNewest(long closed) {
      int newest = (head + size - 1) % needs.length;
      needs[newest] = Math.min(needs[newest], closed);
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
        sum.add(-values[head]);
        head = (head + 1) % times.length;
        size--;
        detector.release();
        evictedSinceExactSum++;
      }
      if (size == detector.retentionLimit()) { // still full: drop the oldest like one leaving by time
        sum.add(-values[head]);
        head = (head + 1) % times.length;
        size--;
        detector.release();
        evictedSinceExactSum++;
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
        sum.add(-values[head]);
        head = (head + 1) % times.length;
        size--;
        detector.release();
        evictedSinceExactSum++;
      }
      if (evictedSinceExactSum >= size) {
        evictedSinceExactSum = 0;
        sum.reset();
        for (int i = 0; i < size; i++) sum.add(values[(head + i) % values.length]);
      }
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
