package io.github.romanhaendler.rbed;

import java.util.OptionalLong;

/**
 * Runtime state machine of one Rule inside one Detector. Evaluates its {@code when} Condition at
 * most once per moment (a Sample or a time advance).
 */
final class RuleState {
  private enum State { IDLE, PENDING, CONFIRMED }

  private final Detector detector;
  private final String name;
  private final EventState events;
  private final Condition.Bound when;
  private final Progress holding;
  private final Condition.Bound end;
  private final Progress ending;
  private final long maxGap;
  private final AggregatedChannels channels = new AggregatedChannels();
  private Accumulator aggregates = new Accumulator();
  private final Accumulator endPhaseAggregates = new Accumulator();
  /** Aggregates of the latest Ended Event, for {@link LatestEvent}; swapped, never copied. */
  private Accumulator latestEnded = new Accumulator();
  private boolean hasEnded;

  private State state = State.IDLE;
  /** {@code when} has stopped holding since the last Event started, so a new one may start. */
  private boolean armed = true;
  /** {@code when} was seen false since observation began or resumed after a gap. */
  private boolean seenFalse;
  private boolean truncatedStart;
  private long confirmedAt;
  private boolean inEndPhase;
  /** The latest moment this Rule was evaluated with data no older than {@code maxGap}. */
  private long lastValidMoment;
  /**
   * Replay Point bookkeeping (see {@link Detector#replayPoint}): where a replay may start so that
   * it is not inside this Rule's latest run (from an Event beginning until {@code when} stopped
   * holding after it), when {@code when} was last seen false, and where the latest Ended Event
   * began.
   */
  private long runStart;
  private long falseSeen;
  private long latestEndedStart;
  private boolean latestRead;

  /** Binds the Rule's Conditions; the Detector must be ready to resolve their references. */
  RuleState(Detector detector, Rule spec) {
    this.detector = detector;
    this.name = spec.name();
    this.events = detector.event(name);
    events.producedBy(this);
    detector.recordChannelLookups();
    this.when = spec.when.condition().bind(detector);
    this.holding = new Progress(spec.when);
    this.end = spec.end == null ? null : spec.end.condition().bind(detector);
    this.ending = spec.end == null ? null : new Progress(spec.end);
    for (ChannelState referenced : detector.recordedChannelLookups()) aggregate(referenced);
    for (Channel channel : spec.aggregated) aggregate(channel.state(detector));
    this.maxGap = spec.maxGap;
    detector.windowMillis(maxGap);
    detector.windowSamples(spec.when.samples());
    detector.windowMillis(spec.when.millis());
    if (spec.end != null) {
      detector.windowSamples(spec.end.samples());
      detector.windowMillis(spec.end.millis());
    }
  }

  /** Keeps Aggregates for this Channel too; returns its index in the Accumulators. */
  int aggregate(ChannelState channel) {
    return channels.indexOf(channel);
  }

  /** A Condition reads Aggregates of this Rule's latest Event, which a replay must reproduce. */
  void latestIsRead() {
    latestRead = true;
  }

  boolean hasMaxGap() {
    return maxGap != Long.MAX_VALUE;
  }

  /**
   * The earliest timestamp a replay must start at for this Rule: before the Event in progress, and
   * before the run of an Event that Ended on its End Condition while {@code when} still holds
   * (a fresh Rule would start another Event); before the moment {@code when} was last seen false
   * (it decides whether the next Event starts truncated); before the latest Ended Event if its
   * Aggregates are read.
   */
  long replayNeed() {
    long need = ValueTrack.NONE;
    if (running()) need = runStart;
    if (seenFalse) need = Math.min(need, falseSeen);
    return Math.min(need, latestNeed());
  }

  /** Before the latest Ended Event, if its Aggregates are read. */
  long latestNeed() {
    return latestRead && hasEnded && state != State.CONFIRMED ? latestEndedStart : ValueTrack.NONE;
  }

  /** From the start of an Event until {@code when} stops holding after it. */
  private boolean running() {
    return state != State.IDLE || !armed;
  }

  /**
   * Where a replay starting at moment {@code now} may start for this Rule: its run began earlier
   * unless it is about to end for a gap in the data.
   */
  long runFrom(long now) {
    boolean gap = detector.hasSamples() && now - detector.lastSample() > maxGap;
    return running() && !gap ? runStart : ValueTrack.NONE;
  }

  /** An Aggregate of the latest Confirmed Event, running or Ended; NaN if there is none. */
  double latest(int channelIndex, Accumulator.Aggregate aggregate) {
    if (state == State.CONFIRMED) return aggregates.aggregate(channelIndex, aggregate);
    return hasEnded ? latestEnded.aggregate(channelIndex, aggregate) : Double.NaN;
  }

  /**
   * Evaluates the current moment: a Sample, or a time advance without one. Advances do not count
   * for {@code forSamples} or Sample-based Aggregates.
   */
  void step(long now, boolean isSample) {
    if (detector.hasSamples() && now - detector.lastSample() > maxGap) {
      close();
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
          if (isSample) holding.advance();
          continueRecording(aggregates, now, isSample);
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
    lastValidMoment = now;
  }

  /**
   * Stops observation: Discards a Pending Event and Ends a Confirmed one, truncated, at the latest
   * moment it was evaluated with valid data.
   */
  void close() {
    if (state == State.PENDING) {
      discard();
    } else if (state == State.CONFIRMED) {
      leaveEndPhase();
      finish(lastValidMoment, true);
    }
  }

  private void discard() {
    state = State.IDLE;
    detector.listener.onDiscarded(snapshot(OptionalLong.empty(), false));
  }

  private void stoppedHolding(Truth holds) {
    armed = true;
    if (holds == Truth.FALSE) {
      seenFalse = true;
      falseSeen = detector.closedNow();
    }
  }

  private void begin(long now, boolean isSample) {
    state = State.PENDING;
    armed = false;
    runStart = detector.closedNow();
    truncatedStart = !seenFalse;
    holding.begin(now, isSample);
    startRecording(aggregates, now, isSample);
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
      continueRecording(aggregates, now, isSample);
    } else {
      finish(now, false);
      stoppedHolding(holds);
    }
  }

  private void stepUntilEndCondition(long now, boolean isSample) {
    if (end.test() != Truth.TRUE) {
      leaveEndPhase();
      continueRecording(aggregates, now, isSample);
      return;
    }
    if (inEndPhase) {
      if (isSample) ending.advance();
      continueRecording(endPhaseAggregates, now, isSample);
    } else {
      inEndPhase = true;
      ending.begin(now, isSample);
      startRecording(endPhaseAggregates, now, isSample);
    }
    if (ending.reached(now)) finish(ending.since, false);
  }

  /** The End Condition stopped holding: its moments belong to the Event after all. */
  private void leaveEndPhase() {
    if (inEndPhase) aggregates.addAll(endPhaseAggregates);
    inEndPhase = false;
  }

  /** Starts Aggregates at a Sample, or at an advance with the values the Channels hold. */
  private void startRecording(Accumulator accumulator, long now, boolean isSample) {
    accumulator.clear();
    if (isSample) accumulator.add(channels, now);
    else accumulator.hold(channels, now);
  }

  /** Only Samples add to Aggregates; across an advance the held values simply continue. */
  private void continueRecording(Accumulator accumulator, long now, boolean isSample) {
    if (isSample) accumulator.add(channels, now);
  }

  private void finish(long endTimestamp, boolean truncated) {
    state = State.IDLE;
    events.end(endTimestamp);
    Event ended = snapshot(
        OptionalLong.of(confirmedAt), OptionalLong.of(endTimestamp), truncated, endTimestamp);
    Accumulator recycled = latestEnded;
    latestEnded = aggregates;
    aggregates = recycled; // cleared when the next Event begins
    hasEnded = true;
    latestEndedStart = runStart;
    detector.ended(ended);
  }

  /** A Pending, Confirmed or Discarded Event, with Aggregates up to the current moment. */
  private Event snapshot(OptionalLong end, boolean truncatedEnd) {
    OptionalLong confirmed =
        state == State.CONFIRMED ? OptionalLong.of(confirmedAt) : OptionalLong.empty();
    return snapshot(confirmed, end, truncatedEnd, detector.now());
  }

  private Event snapshot(
      OptionalLong confirmed, OptionalLong end, boolean truncatedEnd, long aggregatesUntil) {
    return new Event(name, holding.since, confirmed, end, truncatedStart, truncatedEnd,
        aggregates.snapshot(channels, aggregatesUntil));
  }
}
