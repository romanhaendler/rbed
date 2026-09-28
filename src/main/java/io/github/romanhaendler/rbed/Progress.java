package io.github.romanhaendler.rbed;

/** How long a Phase's Condition has held so far. */
final class Progress {
  private final Phase phase;
  long since;
  private int count;

  Progress(Phase phase) {
    this.phase = phase;
  }

  /** @param counted whether the moment it began is a Sample (not a time advance) */
  void begin(long timestamp, boolean counted) {
    since = timestamp;
    count = counted ? 1 : 0;
  }

  void advance() {
    count++;
  }

  boolean reached(long now) {
    return count >= phase.samples() && now - since >= phase.millis();
  }
}
