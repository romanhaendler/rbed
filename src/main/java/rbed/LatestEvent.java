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
  public Expression mean(String channel) {
    return statistic(channel, Accumulator.Statistic.MEAN);
  }

  /** The minimum of a Channel over the latest Event (so far, if it is running). */
  public Expression min(String channel) {
    return statistic(channel, Accumulator.Statistic.MIN);
  }

  /** The maximum of a Channel over the latest Event (so far, if it is running). */
  public Expression max(String channel) {
    return statistic(channel, Accumulator.Statistic.MAX);
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

  private Expression statistic(String channel, Accumulator.Statistic statistic) {
    Objects.requireNonNull(channel, "channel");
    return new Expression() {
      @Override Bound bind(Detector detector) {
        Rule producer = detector.referencedEvent(name).producer;
        if (producer == null) return () -> Double.NaN; // only History, which has no Aggregates
        int index = producer.aggregate(detector.channel(channel));
        return () -> producer.latest(index, statistic);
      }
    };
  }
}
