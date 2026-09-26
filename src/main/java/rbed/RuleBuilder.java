package rbed;

import java.time.Duration;

/** Configures one Rule inside {@link Detector.Builder#rule}. */
public final class RuleBuilder {
  Condition when;
  int whenSamples;
  long whenMillis = -1;

  RuleBuilder() {}

  /** The Conditions under which an Event starts and continues. */
  public RuleBuilder when(Condition condition) {
    when = condition;
    return this;
  }

  /** The preceding Condition must hold for at least {@code n} consecutive Samples. */
  public RuleBuilder forSamples(int n) {
    if (n < 1) throw new IllegalArgumentException("forSamples must be >= 1");
    whenSamples = n;
    return this;
  }

  /** The preceding Condition must hold for at least {@code millis} milliseconds. */
  public RuleBuilder forDuration(long millis) {
    if (millis < 0) throw new IllegalArgumentException("forDuration must be >= 0");
    whenMillis = millis;
    return this;
  }

  /** The preceding Condition must hold for at least {@code duration}. */
  public RuleBuilder forDuration(Duration duration) {
    return forDuration(duration.toMillis());
  }
}
