package rbed;

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
  /** The Rule producing these Events; null for names only known from History. */
  Rule producer;
  private long lastDuration;
  private long runningStart;
  private long endedDuration;
  /** Confirmation times within the largest window any count(...).within(...) needs. */
  private Timestamps confirmations;
  private long countWindow;

  /** Keeps confirmation times for counting within {@code window}; call before any Event. */
  void countWithin(long window) {
    if (confirmations == null) confirmations = new Timestamps();
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

  /** Events Confirmed at most {@code window} before {@code now}. */
  int confirmedWithin(long now, long window) {
    confirmations.dropOlderThan(now, countWindow);
    return confirmations.countWithin(now, window);
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
    if (confirmations != null) confirmations.add(timestamp);
  }
}
