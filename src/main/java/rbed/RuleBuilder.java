package rbed;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Configures one Rule inside {@link Detector.Builder#rule}. {@link #forSamples} and {@link
 * #forDuration} apply to the most recent {@link #when} or {@link #endWhen}; when both are given,
 * both must be met.
 */
public final class RuleBuilder {
  Phase when;
  Phase end;
  long maxGap = Long.MAX_VALUE;
  final Map<String, Function<Event, Object>> attributes = new LinkedHashMap<>();
  final Set<String> aggregated = new LinkedHashSet<>();
  private boolean durationsApplyToEnd;

  RuleBuilder() {}

  /**
   * The Conditions under which an Event starts and, without an End Condition, continues. A new
   * Event only starts after these Conditions have stopped holding since the previous one.
   */
  public RuleBuilder when(Condition condition) {
    when = new Phase(Objects.requireNonNull(condition, "condition"));
    durationsApplyToEnd = false;
    return this;
  }

  /**
   * An End Condition: once Confirmed, the Event continues until this holds (for its own
   * duration), regardless of {@link #when}. The Event's end is where this started holding.
   */
  public RuleBuilder endWhen(Condition condition) {
    end = new Phase(Objects.requireNonNull(condition, "condition"));
    durationsApplyToEnd = true;
    return this;
  }

  /** The preceding Condition must hold for at least {@code n} consecutive Samples. */
  public RuleBuilder forSamples(int n) {
    if (n < 1) throw new IllegalArgumentException("forSamples must be >= 1: " + n);
    return updatePhase(phase -> phase.withSamples(n));
  }

  /** The preceding Condition must hold for at least {@code millis} milliseconds. */
  public RuleBuilder forDuration(long millis) {
    if (millis < 0) throw new IllegalArgumentException("forDuration must be >= 0: " + millis);
    return updatePhase(phase -> phase.withMillis(millis));
  }

  /** The preceding Condition must hold for at least {@code duration}. */
  public RuleBuilder forDuration(Duration duration) {
    return forDuration(duration.toMillis());
  }

  /**
   * If consecutive Samples are more than {@code millis} apart, a Pending Event is Discarded and a
   * Confirmed Event Ends, truncated, at the last Sample before the gap.
   */
  public RuleBuilder maxGap(long millis) {
    if (millis < 0) throw new IllegalArgumentException("maxGap must be >= 0: " + millis);
    maxGap = millis;
    return this;
  }

  /** See {@link #maxGap(long)}. */
  public RuleBuilder maxGap(Duration duration) {
    return maxGap(duration.toMillis());
  }

  /**
   * Adds an attribute computed when the Event Ends, e.g. to classify it from its Aggregates. The
   * function receives the Ended Event (without attributes).
   */
  public RuleBuilder attribute(String name, Function<Event, Object> fn) {
    attributes.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(fn, "fn"));
    return this;
  }

  /**
   * Also keeps Aggregates for these Channels. Channels referenced by the Rule's Conditions are
   * aggregated anyway.
   */
  public RuleBuilder aggregate(String... channels) {
    for (String channel : channels) aggregated.add(Objects.requireNonNull(channel, "channel"));
    return this;
  }

  private RuleBuilder updatePhase(UnaryOperator<Phase> change) {
    if (durationsApplyToEnd) {
      end = change.apply(end);
    } else if (when != null) {
      when = change.apply(when);
    } else {
      throw new IllegalStateException("forSamples/forDuration must follow when() or endWhen()");
    }
    return this;
  }
}
