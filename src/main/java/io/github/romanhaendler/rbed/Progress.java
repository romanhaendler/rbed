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

  void save(StateWriter w) {
    w.int64(since);
    w.int32(count);
  }

  void load(StateReader r) throws java.io.IOException {
    since = r.int64();
    count = r.int32();
  }

  void advance() {
    count++;
  }

  boolean reached(long now) {
    return count >= phase.samples() && now - since >= phase.millis();
  }
}
