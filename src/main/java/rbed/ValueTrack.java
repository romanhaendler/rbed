package rbed;

import java.util.Arrays;

/**
 * The values over time of a Channel, or of a moving average derived from another ValueTrack,
 * inside one Detector. Keeps its last two values and updates the tracks derived from it.
 */
class ValueTrack {
  /** The current value; NaN while undefined. */
  double value = Double.NaN;
  /** The value before the latest update. */
  double previous = Double.NaN;
  /** When the latest update happened. */
  long time;
  /** The value of the latest update; differs from {@link #value} only for an absent pulse. */
  private double latestUpdate = Double.NaN;
  private long previousTime;
  private boolean updated;
  private Averaged[] averages = new Averaged[0];

  void update(long t, double v) {
    previous = latestUpdate;
    previousTime = time;
    latestUpdate = v;
    value = v;
    time = t;
    updated = true;
    for (Averaged average : averages) average.follow(t, v);
  }

  /** True for a Pulse Channel in a moment that does not carry it. */
  boolean absent() {
    return false;
  }

  /** Change per second between the last two values; NaN until there are two. */
  final double rate() {
    return (value - previous) * 1000.0 / (time - previousTime);
  }

  /** Milliseconds since the latest update at {@code now}; NaN before the first. */
  final double age(long now) {
    return updated ? now - time : Double.NaN;
  }

  /** A new track of this one's moving average, updated whenever this one is. */
  final ValueTrack average(Window window) {
    Averaged average = new Averaged(window);
    averages = Arrays.copyOf(averages, averages.length + 1);
    averages[averages.length - 1] = average;
    return average;
  }

  /** A moving average of another track; its window only takes defined values. */
  private static final class Averaged extends ValueTrack {
    private final Window window;

    Averaged(Window window) {
      this.window = window;
    }

    void follow(long t, double source) {
      if (source == source) window.add(t, source); // skip NaN
      update(t, window.average());
    }
  }
}
