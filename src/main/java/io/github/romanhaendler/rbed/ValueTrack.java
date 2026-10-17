package io.github.romanhaendler.rbed;

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
  /**
   * Replay Point bookkeeping, see {@link Detector#replayPoint}: the earliest timestamp a replay
   * must start at to reproduce this track after the latest update and after the one before.
   * Both are settled by {@link #settle} once the moment's replay start is known.
   */
  private long lastNeed = NONE;
  private long previousNeed = NONE;
  private boolean touched;
  /** Read by {@code rate()}, {@code rising()} or {@code falling()}: the previous value matters. */
  boolean needsPrevious;
  /** Read by {@code age()}: the time of the latest update matters. */
  boolean needsAge;

  /** No constraint on the Replay Point. */
  static final long NONE = Long.MAX_VALUE;

  void update(long t, double v) {
    previous = latestUpdate;
    previousTime = time;
    latestUpdate = v;
    value = v;
    time = t;
    updated = true;
    previousNeed = lastNeed;
    lastNeed = NONE; // until settled
    touched = true;
    long windowNeed = windowNeed();
    for (Averaged average : averages) average.follow(t, v, windowNeed);
  }

  /** What the window of a moving average needs; NONE for a Channel. */
  long windowNeed() {
    return NONE;
  }

  /** The need of the latest update, once the moment's replay start is {@code closed}. */
  long updateNeed(long closed) {
    return closed;
  }

  /** Settles the needs of the tracks updated in this moment. */
  final void settle(long closed) {
    if (touched) {
      touched = false;
      settleWindow(closed);
      lastNeed = updateNeed(closed);
    }
    for (Averaged average : averages) average.settle(closed);
  }

  void settleWindow(long closed) {}

  /**
   * The earliest timestamp a replay must start at to reproduce this track and the tracks derived
   * from it: the latest update (and the one before, if the previous value is read), unless the
   * value is an absent pulse nobody reads the age or previous value of.
   */
  final long replayNeed() {
    long need = NONE;
    if (updated) {
      boolean read = needsAge || needsPrevious;
      need = absent() && !read ? NONE : touched ? windowNeed() : lastNeed;
      if (needsPrevious && !absent()) need = Math.min(need, previousNeed);
    }
    for (Averaged average : averages) need = Math.min(need, average.replayNeed());
    return need;
  }

  /** Writes this track's state and, in order, that of the moving averages derived from it. */
  void save(StateWriter w) {
    w.float64(value);
    w.float64(previous);
    w.int64(time);
    w.float64(latestUpdate);
    w.int64(previousTime);
    w.bool(updated);
    w.int64(lastNeed);
    w.int64(previousNeed);
    w.int32(averages.length);
    for (Averaged average : averages) average.save(w);
  }

  void load(StateReader r) throws java.io.IOException {
    value = r.float64();
    previous = r.float64();
    time = r.int64();
    latestUpdate = r.float64();
    previousTime = r.int64();
    updated = r.bool();
    lastNeed = r.int64();
    previousNeed = r.int64();
    r.expect(averages.length);
    for (Averaged average : averages) average.load(r);
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

    @Override
    void save(StateWriter w) {
      super.save(w);
      window.save(w);
    }

    @Override
    void load(StateReader r) throws java.io.IOException {
      super.load(r);
      window.load(r);
    }

    private boolean added;

    void follow(long t, double source, long sourceNeed) {
      added = source == source; // skip NaN
      if (added) window.add(t, source, sourceNeed);
      update(t, window.average());
    }

    @Override
    void settleWindow(long closed) {
      if (added) window.settleNewest(closed);
    }

    @Override
    long updateNeed(long closed) {
      return Math.min(window.need(), needsAge || needsPrevious ? closed : NONE);
    }

    @Override
    long windowNeed() {
      return window.need();
    }
  }
}
