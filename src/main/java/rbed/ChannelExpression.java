package rbed;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The current (last known) value of a Channel, plus Expressions over how the Channel developed.
 * History-based values use the Channel's own values, not the Samples in which it was absent, and
 * are undefined until enough of them exist.
 */
public final class ChannelExpression extends Expression {
  private final String channel;

  ChannelExpression(String channel) {
    this.channel = Objects.requireNonNull(channel, "channel");
  }

  @Override
  Bound bind(Detector detector) {
    ChannelState state = detector.channel(channel);
    return () -> state.value;
  }

  /** True when the Channel's last value is greater than its previous one. */
  public Condition rising() {
    return new Condition() {
      @Override Bound bind(Detector detector) {
        ChannelState state = detector.channel(channel);
        return () -> Truth.greater(state.value, state.previous);
      }
    };
  }

  /** True when the Channel's last value is less than its previous one. */
  public Condition falling() {
    return new Condition() {
      @Override Bound bind(Detector detector) {
        ChannelState state = detector.channel(channel);
        return () -> Truth.less(state.value, state.previous);
      }
    };
  }

  /** Change per second between the Channel's last two values. */
  public Expression rate() {
    return new Expression() {
      @Override Bound bind(Detector detector) {
        return detector.channel(channel)::rate;
      }
    };
  }

  /** Mean of the Channel's last {@code n} values; undefined until {@code n} values exist. */
  public Expression averageOverSamples(int n) {
    if (n < 1) throw new IllegalArgumentException("averageOverSamples needs n >= 1: " + n);
    return average(() -> new Window.OverSamples(n));
  }

  /**
   * Mean of the Channel's values from the last {@code millis} milliseconds, counted back from its
   * latest value; undefined until the Channel's values span that long.
   */
  public Expression averageOverDuration(long millis) {
    if (millis < 0) throw new IllegalArgumentException("averageOverDuration must be >= 0");
    return average(() -> new Window.OverDuration(millis));
  }

  /** See {@link #averageOverDuration(long)}. */
  public Expression averageOverDuration(Duration duration) {
    return averageOverDuration(duration.toMillis());
  }

  /** Each Detector gets its own window, fed with every value of the Channel from the start. */
  private Expression average(Supplier<Window> newWindow) {
    return new Expression() {
      @Override Bound bind(Detector detector) {
        Window window = newWindow.get();
        detector.channel(channel).feed(window);
        return window::average;
      }
    };
  }
}
