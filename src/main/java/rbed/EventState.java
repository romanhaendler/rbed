package rbed;

/** What one Detector knows about the Events of one name, for during, after and unless. */
final class EventState {
  /** An Event of this name is Confirmed and has not Ended. */
  boolean confirmed;
  boolean hasEnded;
  long lastEnd;
  boolean hasBeenConfirmed;
  long lastConfirmed;

  void confirm(long timestamp) {
    confirmed = true;
    hasBeenConfirmed = true;
    lastConfirmed = timestamp;
  }

  void end(long timestamp) {
    confirmed = false;
    hasEnded = true;
    lastEnd = timestamp;
  }

  /** Records an Ended Event from History; order of History Events does not matter. */
  void remember(Event event) {
    long end = event.end().getAsLong();
    long confirmedAt = event.confirmed().getAsLong();
    lastEnd = hasEnded ? Math.max(lastEnd, end) : end;
    lastConfirmed = hasBeenConfirmed ? Math.max(lastConfirmed, confirmedAt) : confirmedAt;
    hasEnded = true;
    hasBeenConfirmed = true;
  }
}
