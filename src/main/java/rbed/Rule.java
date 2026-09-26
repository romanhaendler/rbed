package rbed;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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
  /** The Channels this Rule aggregates; the first {@link #aggregatedCount} are used. */
  private ChannelState[] aggregated = new ChannelState[4];
  private int aggregatedCount;
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
    detector.recordChannelLookups();
    this.when = spec.when.condition().bind(detector);
    this.holding = new Progress(spec.when);
    this.end = spec.end == null ? null : spec.end.condition().bind(detector);
    for (ChannelState referenced : detector.recordedChannelLookups()) aggregate(referenced);
    for (String channel : spec.aggregated) aggregate(detector.channel(channel));
    this.ending = spec.end == null ? null : new Progress(spec.end);
    this.maxGap = spec.maxGap;
    this.attributes = new LinkedHashMap<>(spec.attributes);
  }

  /** Keeps Aggregates for this Channel too; returns its index in the Accumulators. */
  int aggregate(ChannelState channel) {
    for (int i = 0; i < aggregatedCount; i++) if (aggregated[i] == channel) return i;
    if (aggregatedCount == aggregated.length) {
      aggregated = Arrays.copyOf(aggregated, aggregatedCount * 2);
    }
    aggregated[aggregatedCount] = channel;
    return aggregatedCount++;
  }

  /**
   * Processes the current moment: a Sample, or a time advance without one. Advances do not count
   * for {@code forSamples} or Aggregates.
   *
   * @param lastSample timestamp of the latest Sample before this moment; ignored if there is none
   */
  void step(long now, long lastSample, boolean hasLastSample, boolean isSample) {
    if (hasLastSample && now - lastSample > maxGap) {
      close(lastSample);
      armed = true;
      seenFalse = false;
      if (!isSample) return; // values are stale until the next Sample
    }
    switch (state) {
      case IDLE -> {
        Truth holds = when.test();
        if (holds != Truth.TRUE) stoppedHolding(holds);
        else if (armed) begin(now, isSample);
      }
      case PENDING -> {
        Truth holds = when.test();
        if (holds == Truth.TRUE) {
          if (isSample) {
            holding.advance();
            aggregates.add(aggregated, aggregatedCount);
          }
          confirmIfReached(now);
        } else {
          discard();
          stoppedHolding(holds);
        }
      }
      case CONFIRMED -> {
        if (end == null) stepUntilWhenStops(now, isSample);
        else stepUntilEndCondition(now, isSample);
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

  private void begin(long now, boolean isSample) {
    state = State.PENDING;
    armed = false;
    truncatedStart = !seenFalse;
    holding.begin(now, isSample);
    aggregates.clear();
    if (isSample) aggregates.add(aggregated, aggregatedCount);
    detector.listener.onPending(snapshot(OptionalLong.empty(), false));
    confirmIfReached(now);
  }

  private void confirmIfReached(long now) {
    if (!holding.reached(now)) return;
    state = State.CONFIRMED;
    confirmedAt = now;
    inEndPhase = false;
    events.confirm(now, holding.since);
    detector.listener.onConfirmed(snapshot(OptionalLong.empty(), false));
  }

  private void stepUntilWhenStops(long now, boolean isSample) {
    Truth holds = when.test();
    if (holds == Truth.TRUE) {
      if (isSample) aggregates.add(aggregated, aggregatedCount);
    } else {
      finish(now, false);
      stoppedHolding(holds);
    }
  }

  private void stepUntilEndCondition(long now, boolean isSample) {
    if (end.test() != Truth.TRUE) {
      leaveEndPhase();
      if (isSample) aggregates.add(aggregated, aggregatedCount);
      return;
    }
    if (!inEndPhase) {
      inEndPhase = true;
      ending.begin(now, isSample);
      endPhaseAggregates.clear();
    } else if (isSample) {
      ending.advance();
    }
    if (isSample) endPhaseAggregates.add(aggregated, aggregatedCount);
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
        aggregates.snapshot(aggregated, aggregatedCount), Map.of());
  }
}
