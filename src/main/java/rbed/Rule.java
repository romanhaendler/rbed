package rbed;

import java.util.OptionalLong;

/** Runtime state machine of one Rule inside one Detector. */
final class Rule {
  private enum State { IDLE, PENDING, CONFIRMED }

  private final String name;
  private final Condition when;
  private final int whenSamples;
  private final long whenMillis;

  private State state = State.IDLE;
  private long start;
  private int count;
  private boolean truncatedStart;

  Rule(String name, RuleBuilder b) {
    this.name = name;
    this.when = b.when;
    this.whenSamples = b.whenSamples;
    this.whenMillis = b.whenMillis;
  }

  void step(Detector d, long now, boolean first) {
    boolean holds = Boolean.TRUE.equals(when.test(d));
    if (state == State.CONFIRMED && !holds) {
      end(d, now, false);
    } else if (state == State.PENDING) {
      if (holds) {
        count++;
        confirmIfReady(d, now);
      } else {
        discard(d);
      }
    } else if (state == State.IDLE && holds) {
      state = State.PENDING;
      start = now;
      count = 1;
      truncatedStart = first;
      d.listener.onPending(snapshot(OptionalLong.empty(), false));
      confirmIfReady(d, now);
    }
  }

  void close(Detector d, long last) {
    if (state == State.PENDING) discard(d);
    if (state == State.CONFIRMED) end(d, last, true);
  }

  private void confirmIfReady(Detector d, long now) {
    if (count >= whenSamples && now - start >= whenMillis) {
      state = State.CONFIRMED;
      d.listener.onConfirmed(snapshot(OptionalLong.empty(), false));
    }
  }

  private void discard(Detector d) {
    state = State.IDLE;
    d.listener.onDiscarded(snapshot(OptionalLong.empty(), false));
  }

  private void end(Detector d, long end, boolean truncated) {
    state = State.IDLE;
    d.listener.onEnded(snapshot(OptionalLong.of(end), truncated));
  }

  private Event snapshot(OptionalLong end, boolean truncatedEnd) {
    return new Event(name, start, end, truncatedStart, truncatedEnd);
  }
}
