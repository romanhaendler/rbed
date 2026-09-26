package rbed;

import java.util.OptionalLong;

/** Runtime state machine of one Rule inside one Detector. */
final class Rule {
  private enum State { IDLE, PENDING, CONFIRMED }

  private final String name;
  private final Condition when;
  private final int whenSamples;
  private final long whenMillis;
  private final Condition endWhen;
  private final int endSamples;
  private final long endMillis;
  private final long maxGap;

  private State state = State.IDLE;
  private long start;
  private int count;
  private boolean truncatedStart;
  private boolean inEndPhase;
  private long endPhaseStart;
  private int endCount;

  Rule(String name, RuleBuilder b) {
    this.name = name;
    this.when = b.when;
    this.whenSamples = b.whenSamples;
    this.whenMillis = b.whenMillis;
    this.endWhen = b.endWhen;
    this.endSamples = b.endSamples;
    this.endMillis = b.endMillis;
    this.maxGap = b.maxGap;
  }

  /**
   * @param previous timestamp of the previous Sample, or null for the Detector's first Sample
   */
  void step(Detector d, long now, Long previous) {
    if (previous != null && now - previous > maxGap) close(d, previous);
    if (state == State.CONFIRMED) stepConfirmed(d, now);
    if (state == State.PENDING) {
      if (holds(when, d)) {
        count++;
        confirmIfReady(d, now);
      } else {
        discard(d);
      }
    } else if (state == State.IDLE && holds(when, d)) {
      state = State.PENDING;
      start = now;
      count = 1;
      truncatedStart = previous == null;
      d.listener.onPending(snapshot(OptionalLong.empty(), false));
      confirmIfReady(d, now);
    }
  }

  /** Stops observation: Discards a Pending Event and truncates a Confirmed one at {@code last}. */
  void close(Detector d, long last) {
    if (state == State.PENDING) discard(d);
    if (state == State.CONFIRMED) end(d, last, true);
  }

  private void stepConfirmed(Detector d, long now) {
    if (endWhen == null) {
      if (!holds(when, d)) end(d, now, false);
      return;
    }
    if (!holds(endWhen, d)) {
      inEndPhase = false;
      return;
    }
    if (!inEndPhase) {
      inEndPhase = true;
      endPhaseStart = now;
      endCount = 0;
    }
    endCount++;
    if (endCount >= endSamples && now - endPhaseStart >= endMillis) end(d, endPhaseStart, false);
  }

  private void confirmIfReady(Detector d, long now) {
    if (count >= whenSamples && now - start >= whenMillis) {
      state = State.CONFIRMED;
      inEndPhase = false;
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

  private static boolean holds(Condition c, Detector d) {
    return Boolean.TRUE.equals(c.test(d));
  }

  private Event snapshot(OptionalLong end, boolean truncatedEnd) {
    return new Event(name, start, end, truncatedStart, truncatedEnd);
  }
}
