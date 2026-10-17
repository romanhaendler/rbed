package io.github.romanhaendler.rbed;

/**
 * What one Detector knows about the Events of one name, for during, after, unless, count and
 * totalDuration. History Events count like Events that Ended before the first Sample.
 */
final class EventState {
  /** An Event of this name is Confirmed and has not Ended. */
  boolean confirmed;
  boolean hasEnded;
  long lastEnd;
  boolean hasBeenConfirmed;
  long lastConfirmed;
  /** Events Confirmed so far. */
  long count;
  /** The Rule producing these Events. */
  private RuleState producer;
  private long lastDuration;
  private long runningStart;
  private long endedDuration;
  private final Detector detector;
  private final String name;
  /** Confirmation times within the largest window any count(...).within(...) needs. */
  private Timestamps confirmations;
  private long countWindow;

  EventState(Detector detector, String name) {
    this.detector = detector;
    this.name = name;
  }

  void save(StateWriter w) {
    w.bool(confirmed);
    w.bool(hasEnded);
    w.int64(lastEnd);
    w.bool(hasBeenConfirmed);
    w.int64(lastConfirmed);
    w.int64(count);
    w.int64(lastDuration);
    w.int64(runningStart);
    w.int64(endedDuration);
    w.bool(confirmations != null);
    if (confirmations != null) confirmations.save(w);
  }

  void load(StateReader r) throws java.io.IOException {
    confirmed = r.bool();
    hasEnded = r.bool();
    lastEnd = r.int64();
    hasBeenConfirmed = r.bool();
    lastConfirmed = r.int64();
    count = r.int64();
    lastDuration = r.int64();
    runningStart = r.int64();
    endedDuration = r.int64();
    if (r.bool() != (confirmations != null)) throw new java.io.IOException("count timestamps");
    if (confirmations != null) confirmations.load(r);
  }

  void producedBy(RuleState rule) {
    producer = rule;
  }

  /**
   * An Aggregate of a Channel over the latest Confirmed Event, running or Ended; the producing
   * Rule starts aggregating the Channel.
   */
  Expression.Bound latest(ChannelState channel, Accumulator.Aggregate aggregate) {
    RuleState rule = producer;
    int index = rule.aggregate(channel);
    return () -> rule.latest(index, aggregate);
  }

  /** Keeps confirmation times for counting within {@code window}; call before any Event. */
  void countWithin(long window) {
    if (confirmations == null) confirmations = new Timestamps(detector, name);
    countWindow = Math.max(countWindow, window);
  }

  void confirm(long timestamp, long start) {
    confirmed = true;
    runningStart = start;
    recordConfirmation(timestamp);
  }

  void end(long timestamp) {
    confirmed = false;
    hasEnded = true;
    lastEnd = timestamp;
    lastDuration = timestamp - runningStart;
    endedDuration += lastDuration;
  }

  /** Records an Ended Event from History; History must be given in order of confirmation. */
  void remember(Event event) {
    long end = event.end().getAsLong();
    recordConfirmation(event.confirmed().getAsLong());
    if (!hasEnded || end >= lastEnd) {
      lastEnd = end;
      lastDuration = end - event.start();
    }
    hasEnded = true;
    endedDuration += end - event.start();
  }

  /** Counts Events Confirmed at most {@code window} before the current moment; see {@link #countWithin}. */
  Timestamps.Counter counter(long window) {
    return confirmations.new Counter(window);
  }

  /** Drops confirmation times no window needs any more. */
  void dropExpired(long now) {
    confirmations.dropOlderThan(now, countWindow);
  }

  /** Duration of the latest Confirmed Event, running up to {@code now}; NaN if there is none. */
  double latestDuration(long now) {
    if (confirmed) return now - runningStart;
    return hasEnded ? lastDuration : Double.NaN;
  }

  /** Summed duration of all Ended Events plus the running one up to {@code now}. */
  long totalDuration(long now) {
    return endedDuration + (confirmed ? now - runningStart : 0);
  }

  private void recordConfirmation(long timestamp) {
    lastConfirmed = hasBeenConfirmed ? Math.max(lastConfirmed, timestamp) : timestamp;
    hasBeenConfirmed = true;
    count++;
    if (confirmations != null) {
      confirmations.dropOlderThan(timestamp, countWindow); // bounded even if never evaluated
      confirmations.add(timestamp);
    }
  }
}
