package io.github.romanhaendler.rbed;

import java.util.Map;
import java.util.OptionalLong;

/**
 * An immutable snapshot of a detected occurrence.
 *
 * @param name the name of the Rule that produced it
 * @param start timestamp (ms) of the first Sample at which the Rule's Conditions held
 * @param confirmed timestamp (ms) at which it was Confirmed; empty while Pending or if Discarded
 * @param end timestamp (ms) at which it Ended; empty until Ended
 * @param truncatedStart it may have begun before its start: the Rule had not yet seen its
 *     Conditions not holding since observation began or resumed after a gap
 * @param truncatedEnd observation stopped (close or max gap) while it was in progress
 * @param aggregates Aggregates over its Samples so far, for each Channel its Rule aggregates
 */
public record Event(
    String name,
    long start,
    OptionalLong confirmed,
    OptionalLong end,
    boolean truncatedStart,
    boolean truncatedEnd,
    Map<Channel, Aggregates> aggregates) {

  /** Whether this Event was produced by the given Rule, i.e. has its name. */
  public boolean is(Rule rule) {
    return name.equals(rule.name());
  }

  /**
   * Aggregates of one Channel; {@link Aggregates#count()} is 0 if it had no value.
   *
   * @throws IllegalArgumentException if the Rule does not aggregate this Channel
   */
  public Aggregates aggregates(Channel channel) {
    Aggregates a = aggregates.get(channel);
    if (a == null) {
      throw new IllegalArgumentException("Rule " + name + " does not aggregate channel " + channel
          + "; add it with aggregate(...)");
    }
    return a;
  }
}
