package rbed;

import java.time.Duration;

/**
 * Configures one Rule inside {@link Detector.Builder#rule}. {@link #forSamples} and {@link
 * #forDuration} apply to the most recent {@link #when} or {@link #endWhen}.
 */
public final class RuleBuilder {
  Condition when;
  int whenSamples;
  long whenMillis = -1;
  Condition endWhen;
  int endSamples;
  long endMillis = -1;
  long maxGap = Long.MAX_VALUE;
  private boolean configuringEnd;

  RuleBuilder() {}

  /** The Conditions under which an Event starts and, without an End Condition, continues. */
  public RuleBuilder when(Condition condition) {
    when = condition;
    configuringEnd = false;
    return this;
  }

  /**
   * An End Condition: once Confirmed, the Event continues until this holds (for its own
   * duration), regardless of {@link #when}. The Event's end is where this started holding.
   */
  public RuleBuilder endWhen(Condition condition) {
    endWhen = condition;
    configuringEnd = true;
    return this;
  }

  /** The preceding Condition must hold for at least {@code n} consecutive Samples. */
  public RuleBuilder forSamples(int n) {
    if (n < 1) throw new IllegalArgumentException("forSamples must be >= 1");
    if (configuringEnd) endSamples = n;
    else whenSamples = n;
    return this;
  }

  /** The preceding Condition must hold for at least {@code millis} milliseconds. */
  public RuleBuilder forDuration(long millis) {
    if (millis < 0) throw new IllegalArgumentException("forDuration must be >= 0");
    if (configuringEnd) endMillis = millis;
    else whenMillis = millis;
    return this;
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
    if (millis < 0) throw new IllegalArgumentException("maxGap must be >= 0");
    maxGap = millis;
    return this;
  }

  /** See {@link #maxGap(long)}. */
  public RuleBuilder maxGap(Duration duration) {
    return maxGap(duration.toMillis());
  }
}
