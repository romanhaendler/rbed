package io.github.romanhaendler.rbed;

import java.util.Locale;
import java.util.Objects;

/**
 * Values of the most recent Event of a given Rule that was Confirmed, whether still running or
 * Ended: its Aggregates and its duration. Undefined while there is none. History Events have no
 * Aggregates, only a duration. Create with {@link Conditions#latest}.
 */
public final class LatestEvent {
  private final Rule rule;

  LatestEvent(Rule rule) {
    this.rule = Objects.requireNonNull(rule, "rule");
  }

  /** The mean of a Channel over the latest Event (so far, if it is running). */
  public Expression mean(Channel channel) {
    return aggregate(channel, Accumulator.Aggregate.MEAN);
  }

  /** The minimum of a Channel over the latest Event (so far, if it is running). */
  public Expression min(Channel channel) {
    return aggregate(channel, Accumulator.Aggregate.MIN);
  }

  /** The maximum of a Channel over the latest Event (so far, if it is running). */
  public Expression max(Channel channel) {
    return aggregate(channel, Accumulator.Aggregate.MAX);
  }

  /** The latest Event's duration in milliseconds; for a running Event, up to now. */
  public Expression duration() {
    return new Expression("latest(" + rule + ").duration()") {
      @Override Bound bind(Detector detector) {
        EventState events = detector.referencedEvent(rule);
        return () -> events.latestDuration(detector.now());
      }
    };
  }

  private Expression aggregate(Channel channel, Accumulator.Aggregate aggregate) {
    Objects.requireNonNull(channel, "channel");
    String call = aggregate.name().toLowerCase(Locale.ROOT) + "(" + channel + ")";
    return new Expression("latest(" + rule + ")." + call) {
      @Override Bound bind(Detector detector) {
        // not a Channel lookup of the reading Rule: the referenced Rule aggregates it
        return detector.referencedEvent(rule).latest(channel.state(detector), aggregate);
      }
    };
  }
}
