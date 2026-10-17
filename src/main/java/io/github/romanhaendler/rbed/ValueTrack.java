package io.github.romanhaendler.rbed;

import java.io.IOException;
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
   * must start at to reproduce this track after the latest update, and after the one before. An
   * update needs what its value depends on (a Channel: the Sample itself; a moving average: the
   * oldest entry of its window) and never more: whatever else is going on in that moment is
   * recorded where it belongs.
   */
  private long lastNeed = NONE;
  private long previousNeed = NONE;
  /** Read by {@code rate()}, {@code rising()} or {@code falling()}: the previous value matters. */
  boolean needsPrevious;
  /** Read by {@code age()}: the time of the latest update matters. */
  boolean needsAge;

  /** No constraint on the Replay Point. */
  static final long NONE = Long.MAX_VALUE;

  /**
   * @param need the earliest timestamp a replay must start at to reproduce this update
   */
  void update(long t, double v, long need) {
    previous = latestUpdate;
    previousTime = time;
    latestUpdate = v;
    value = v;
    time = t;
    updated = true;
    previousNeed = lastNeed;
    lastNeed = need;
    for (Averaged average : averages) average.follow(t, v, need);
  }

  /** What a reader of a track reads besides its value; see {@link #need}. */
  static final int PREVIOUS = 1;
  static final int AGE = 2;

  /**
   * The earliest timestamp a replay must start at to reproduce this track for a reader that
   * reads its value and, as {@code reads} says, the previous value and/or the age: the latest
   * update (and the one before, if the previous value is read). An absent pulse has no value to
   * reproduce and no previous value, only an age; but if the reader goes on reading it later
   * ({@code later}), the previous value at the next pulse is this track's latest update.
   */
  final long need(int reads, boolean later) {
    if (!updated) return NONE;
    if (absent() && (reads & AGE) == 0 && !(later && (reads & PREVIOUS) != 0)) return NONE;
    long need = lastNeed;
    if ((reads & PREVIOUS) != 0 && !absent()) need = Math.min(need, previousNeed);
    return need;
  }

  /**
   * What the state of this track needs for what comes next: its latest update (and the one
   * before, if the previous value is read), unless the value is an absent pulse nobody reads the
   * age or previous value of: the next pulse then does not depend on this one.
   */
  final long ownNeed() {
    if (!updated) return NONE;
    boolean read = needsAge || needsPrevious;
    long need = absent() && !read ? NONE : lastNeed;
    if (needsPrevious && !absent()) need = Math.min(need, previousNeed);
    return need;
  }

  /** {@link #ownNeed()} of this track and of the moving averages derived from it. */
  final long replayNeed() {
    long need = ownNeed();
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

  void load(StateReader r) throws IOException {
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
    void load(StateReader r) throws IOException {
      super.load(r);
      window.load(r);
    }

    void follow(long t, double source, long sourceNeed) {
      boolean added = source == source; // skip NaN
      if (added) window.add(t, source, sourceNeed);
      // the age and the previous value also depend on the update itself having happened
      long need = Math.min(window.need(), needsAge || needsPrevious ? sourceNeed : NONE);
      update(t, window.average(), need);
    }
  }
}
