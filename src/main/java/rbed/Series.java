package rbed;

import java.util.Arrays;

/**
 * Values over time inside one Detector: a Channel, or a moving average derived from another
 * Series. Keeps its last two values and updates the Series derived from it.
 */
class Series {
  /** The current value; NaN while undefined. */
  double value = Double.NaN;
  double previous = Double.NaN;
  /** When {@link #value} was last updated. */
  long time;
  private long previousTime;
  private boolean updated;
  private Averaged[] derived = new Averaged[0];

  void add(long t, double v) {
    previous = value;
    previousTime = time;
    value = v;
    time = t;
    updated = true;
    for (Averaged series : derived) series.update(t, v);
  }

  /** Change per second between the last two values; NaN until there are two. */
  final double rate() {
    return (value - previous) * 1000.0 / (time - previousTime);
  }

  /** Milliseconds since the last update at {@code now}; NaN before the first. */
  final double age(long now) {
    return updated ? now - time : Double.NaN;
  }

  /** A new Series of this one's moving average, updated whenever this one is. */
  final Series average(Window window) {
    Averaged series = new Averaged(window);
    derived = Arrays.copyOf(derived, derived.length + 1);
    derived[derived.length - 1] = series;
    return series;
  }

  /** A moving average of another Series; its window only takes defined values. */
  private static final class Averaged extends Series {
    private final Window window;

    Averaged(Window window) {
      this.window = window;
    }

    void update(long t, double source) {
      if (source == source) window.add(t, source); // skip NaN
      add(t, window.average());
    }
  }
}
