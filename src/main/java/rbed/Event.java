package rbed;

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
 * @param aggregates per-Channel Aggregates over its Samples so far
 * @param attributes values of the Rule's attributes; empty until Ended
 */
public record Event(
    String name,
    long start,
    OptionalLong confirmed,
    OptionalLong end,
    boolean truncatedStart,
    boolean truncatedEnd,
    Map<String, Aggregates> aggregates,
    Map<String, Object> attributes) {

  /** Aggregates of one Channel; {@link Aggregates#count()} is 0 if it had no value. */
  public Aggregates aggregates(String channel) {
    return aggregates.getOrDefault(channel, Aggregates.EMPTY);
  }

  Event withAttributes(Map<String, Object> values) {
    return new Event(
        name, start, confirmed, end, truncatedStart, truncatedEnd, aggregates, values);
  }
}
