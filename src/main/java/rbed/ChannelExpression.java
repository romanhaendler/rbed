package rbed;

import java.time.Duration;

/**
 * The current (last known) value of a Channel, plus Expressions over how the Channel developed.
 * History-based values are undefined until enough values of the Channel exist.
 */
public final class ChannelExpression extends Expression {
  final String channel;

  ChannelExpression(String channel) {
    this.channel = channel;
  }

  @Override
  double value(Detector d) {
    return d.channel(channel).value;
  }

  /** True when the Channel's last value is greater than its previous one. */
  public Condition rising() {
    return compare(this, previous(), 1);
  }

  /** True when the Channel's last value is less than its previous one. */
  public Condition falling() {
    return compare(this, previous(), -1);
  }

  /** Change per second between the Channel's last two values. */
  public Expression rate() {
    return new Expression(this) {
      @Override double value(Detector d) {
        Detector.ChannelState s = d.channel(channel);
        return (s.value - s.previous) * 1000.0 / (s.time - s.previousTime);
      }
    };
  }

  /** Mean of the Channel's last {@code n} values; undefined until {@code n} values exist. */
  public Expression averageOverSamples(int n) {
    if (n < 1) throw new IllegalArgumentException("averageOverSamples needs n >= 1");
    return new MovingAverage(this, n, -1);
  }

  /**
   * Mean of the Channel's values from the last {@code millis} milliseconds (relative to its last
   * value); undefined until the Channel has values spanning that long.
   */
  public Expression averageOverDuration(long millis) {
    if (millis < 0) throw new IllegalArgumentException("averageOverDuration needs millis >= 0");
    return new MovingAverage(this, 0, millis);
  }

  /** See {@link #averageOverDuration(long)}. */
  public Expression averageOverDuration(Duration duration) {
    return averageOverDuration(duration.toMillis());
  }

  private Expression previous() {
    return new Expression(this) {
      @Override double value(Detector d) { return d.channel(channel).previous; }
    };
  }
}
