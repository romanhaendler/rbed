package rbed;

import java.util.OptionalLong;

/** Runtime state machine of one Rule inside one Detector. */
final class Rule {
  private enum State { IDLE, CONFIRMED }

  private final String name;
  private final Condition when;
  private State state = State.IDLE;
  private long start;
  private boolean truncatedStart;

  Rule(String name, RuleBuilder b) {
    this.name = name;
    this.when = b.when;
  }

  void step(Detector d, long now, boolean first) {
    boolean holds = Boolean.TRUE.equals(when.test(d));
    if (state == State.CONFIRMED && !holds) {
      end(d, now, false);
    }
    if (state == State.IDLE && holds) {
      start = now;
      truncatedStart = first;
      d.listener.onPending(snapshot(OptionalLong.empty(), false));
      state = State.CONFIRMED;
      d.listener.onConfirmed(snapshot(OptionalLong.empty(), false));
    }
  }

  void close(Detector d, long last) {
    if (state == State.CONFIRMED) end(d, last, true);
  }

  private void end(Detector d, long end, boolean truncated) {
    state = State.IDLE;
    d.listener.onEnded(snapshot(OptionalLong.of(end), truncated));
  }

  private Event snapshot(OptionalLong end, boolean truncatedEnd) {
    return new Event(name, start, end, truncatedStart, truncatedEnd);
  }
}
