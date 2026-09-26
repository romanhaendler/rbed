package rbed;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * Runtime state machine of one Rule inside one Detector. Evaluates its {@code when} Condition at
 * most once per Sample.
 */
final class Rule {
  private enum State { IDLE, PENDING, CONFIRMED }

  private final Detector detector;
  private final String name;
  private final EventState events;
  private final Condition.Bound when;
  private final Progress holding;
  private final Condition.Bound end;
  private final Progress ending;
  private final long maxGap;
  private final Map<String, Function<Event, Object>> attributes;
  private final List<ChannelState> channels;
  private final Accumulator aggregates = new Accumulator();
  private final Accumulator endPhaseAggregates = new Accumulator();

  private State state = State.IDLE;
  /** {@code when} has stopped holding since the last Event started, so a new one may start. */
  private boolean armed = true;
  /** {@code when} was seen false since observation began or resumed after a gap. */
  private boolean seenFalse;
  private boolean truncatedStart;
  private long confirmedAt;
  private boolean inEndPhase;

  /** Binds the Rule's Conditions; the Detector must be ready to resolve their references. */
  Rule(Detector detector, String name, RuleBuilder spec) {
    this.detector = detector;
    this.name = name;
    this.events = detector.event(name);
    this.when = spec.when.condition().bind(detector);
    this.holding = new Progress(spec.when);
    this.end = spec.end == null ? null : spec.end.condition().bind(detector);
    this.ending = spec.end == null ? null : new Progress(spec.end);
    this.maxGap = spec.maxGap;
    this.attributes = new LinkedHashMap<>(spec.attributes);
    this.channels = detector.channels();
  }

  /**
   * Processes the current Sample.
   *
   * @param previous timestamp of the previous Sample; ignored when {@code first}
   */
  void step(long now, long previous, boolean first) {
    if (!first && now - previous > maxGap) {
      close(previous);
      armed = true;
      seenFalse = false;
    }
    switch (state) {
      case IDLE -> {
        Truth holds = when.test();
        if (holds != Truth.TRUE) stoppedHolding(holds);
        else if (armed) begin(now);
      }
      case PENDING -> {
        Truth holds = when.test();
        if (holds == Truth.TRUE) {
          holding.advance();
          aggregates.add(channels);
          confirmIfReached(now);
        } else {
          discard();
          stoppedHolding(holds);
        }
      }
      case CONFIRMED -> {
        if (end == null) stepUntilWhenStops(now);
        else stepUntilEndCondition(now);
      }
    }
  }

  /** Stops observation: Discards a Pending Event and truncates a Confirmed one at {@code last}. */
  void close(long last) {
    if (state == State.PENDING) {
      discard();
    } else if (state == State.CONFIRMED) {
      leaveEndPhase();
      finish(last, true);
    }
  }

  private void discard() {
    state = State.IDLE;
    detector.listener.onDiscarded(snapshot(OptionalLong.empty(), false));
  }

  private void stoppedHolding(Truth holds) {
    armed = true;
    if (holds == Truth.FALSE) seenFalse = true;
  }

  private void begin(long now) {
    state = State.PENDING;
    armed = false;
    truncatedStart = !seenFalse;
    holding.begin(now);
    aggregates.clear();
    aggregates.add(channels);
    detector.listener.onPending(snapshot(OptionalLong.empty(), false));
    confirmIfReached(now);
  }

  private void confirmIfReached(long now) {
    if (!holding.reached(now)) return;
    state = State.CONFIRMED;
    confirmedAt = now;
    inEndPhase = false;
    events.confirm(now);
    detector.listener.onConfirmed(snapshot(OptionalLong.empty(), false));
  }

  private void stepUntilWhenStops(long now) {
    Truth holds = when.test();
    if (holds == Truth.TRUE) {
      aggregates.add(channels);
    } else {
      finish(now, false);
      stoppedHolding(holds);
    }
  }

  private void stepUntilEndCondition(long now) {
    if (end.test() != Truth.TRUE) {
      leaveEndPhase();
      aggregates.add(channels);
      return;
    }
    if (inEndPhase) {
      ending.advance();
    } else {
      inEndPhase = true;
      ending.begin(now);
      endPhaseAggregates.clear();
    }
    endPhaseAggregates.add(channels);
    if (ending.reached(now)) finish(ending.since, false);
  }

  /** The End Condition stopped holding: its Samples belong to the Event after all. */
  private void leaveEndPhase() {
    if (inEndPhase) aggregates.addAll(endPhaseAggregates);
    inEndPhase = false;
  }

  private void finish(long endTimestamp, boolean truncated) {
    state = State.IDLE;
    events.end(endTimestamp);
    Event ended = snapshot(OptionalLong.of(confirmedAt), OptionalLong.of(endTimestamp), truncated);
    if (!attributes.isEmpty()) {
      Map<String, Object> values = new LinkedHashMap<>();
      for (Map.Entry<String, Function<Event, Object>> a : attributes.entrySet()) {
        values.put(a.getKey(), a.getValue().apply(ended));
      }
      ended = ended.withAttributes(Collections.unmodifiableMap(values));
    }
    detector.ended(ended);
  }

  private Event snapshot(OptionalLong end, boolean truncatedEnd) {
    OptionalLong confirmed =
        state == State.CONFIRMED ? OptionalLong.of(confirmedAt) : OptionalLong.empty();
    return snapshot(confirmed, end, truncatedEnd);
  }

  private Event snapshot(OptionalLong confirmed, OptionalLong end, boolean truncatedEnd) {
    return new Event(name, holding.since, confirmed, end, truncatedStart, truncatedEnd,
        aggregates.snapshot(channels), Map.of());
  }
}
