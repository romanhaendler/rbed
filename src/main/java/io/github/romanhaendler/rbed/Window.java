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

  abstract void add(long time, double value);

  /** The mean of the window, or NaN while it is warming up. */
  abstract double average();

  /** The last {@code n} values. */
  static final class OverSamples extends Window {
    private final double[] values;
    private int size;
    private int next;
    private int evictedSinceExactSum;
    private final CompensatedSum sum = new CompensatedSum();

    OverSamples(Detector detector, int n) {
      super(detector);
      values = new double[n];
    }

    @Override
    void add(long time, double value) {
      if (size == values.length) {
        sum.add(-values[next]);
        evictedSinceExactSum++;
      } else {
        size++;
        detector.retain();
      }
      values[next] = value;
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
    private final String rule;
    private long[] times;
    private double[] values;
    private int head;
    private int size;
    private int evictedSinceExactSum;
    private final CompensatedSum sum = new CompensatedSum();
    private boolean started;
    private long first;

    OverDuration(Detector detector, long millis) {
      super(detector);
      this.millis = millis;
      rule = "Rule " + detector.bindingRule;
      int initial = Math.min(16, detector.retentionLimit());
      times = new long[initial];
      values = new double[initial];
    }

    @Override
    void add(long time, double value) {
      if (!started) {
        started = true;
        first = time;
      }
      // evict by time first: an entry leaving by time is not a drop
      while (size > 0 && time - times[head] > millis) {
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
        detector.recordDrop(rule);
      }
      if (size == times.length) grow();
      int tail = (head + size) % times.length;
      times[tail] = time;
      values[tail] = value;
      size++;
      detector.retain();
      sum.add(value);
      while (time - times[head] > millis) {
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
      for (int i = 0; i < size; i++) {
        newTimes[i] = times[(head + i) % times.length];
        newValues[i] = values[(head + i) % values.length];
      }
      times = newTimes;
      values = newValues;
      head = 0;
    }
  }
}
