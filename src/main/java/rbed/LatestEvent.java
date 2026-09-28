package rbed;

import java.util.Objects;

/**
 * Values of the most recent Event with a given name that was Confirmed, whether still running or
 * Ended: its Aggregates and its duration. Undefined while there is none. History Events have no
 * Aggregates, only a duration. Create with {@link Conditions#latest}.
 */
public final class LatestEvent {
  private final String name;

  LatestEvent(String name) {
    this.name = Objects.requireNonNull(name, "eventName");
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
    return new Expression() {
      @Override Bound bind(Detector detector) {
        EventState events = detector.referencedEvent(name);
        return () -> events.latestDuration(detector.now());
      }
    };
  }

  private Expression aggregate(Channel channel, Accumulator.Aggregate aggregate) {
    Objects.requireNonNull(channel, "channel");
    return new Expression() {
      @Override Bound bind(Detector detector) {
        // not a Channel lookup of the reading Rule: the referenced Rule aggregates it
        return detector.referencedEvent(name).latest(channel.state(detector), aggregate);
      }
    };
  }
}
