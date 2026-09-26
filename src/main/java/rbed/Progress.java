package rbed;

/** How long a Phase's Condition has held so far. */
final class Progress {
  private final Phase phase;
  long since;
  private int count;

  Progress(Phase phase) {
    this.phase = phase;
  }

  void begin(long timestamp) {
    since = timestamp;
    count = 1;
  }

  void advance() {
    count++;
  }

  boolean reached(long now) {
    return count >= phase.samples() && now - since >= phase.millis();
  }
}
