package io.github.romanhaendler.rbed;

/**
 * The recent values of one Channel for a moving average. Memory is bounded by the window size and
 * nothing is allocated per value once the window is full. The running sum is compensated, so a
 * large value leaving the window does not wipe out small ones, and it is recomputed from the
 * window each time the window has turned over, so rounding error cannot build up on endless
 * streams. Both keep the amortized cost per value constant.
 */
abstract class Window {
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

    OverSamples(int n) {
      values = new double[n];
    }

    @Override
    void add(long time, double value) {
      if (size == values.length) {
        sum.add(-values[next]);
        evictedSinceExactSum++;
      } else {
        size++;
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
    private long[] times = new long[16];
    private double[] values = new double[16];
    private int head;
    private int size;
    private int evictedSinceExactSum;
    private final CompensatedSum sum = new CompensatedSum();
    private boolean started;
    private long first;

    OverDuration(long millis) {
      this.millis = millis;
    }

    @Override
    void add(long time, double value) {
      if (!started) {
        started = true;
        first = time;
      }
      if (size == times.length) grow();
      int tail = (head + size) % times.length;
      times[tail] = time;
      values[tail] = value;
      size++;
      sum.add(value);
      while (time - times[head] > millis) {
        sum.add(-values[head]);
        head = (head + 1) % times.length;
        size--;
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
      long[] newTimes = new long[times.length * 2];
      double[] newValues = new double[values.length * 2];
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
