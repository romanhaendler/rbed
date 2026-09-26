package rbed;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The current value of a Channel, or of a moving average derived from one, plus Expressions over
 * how it developed. History-based values use the series' own updates (a Channel's values, not the
 * Samples in which it was absent) and are undefined until enough of them exist.
 */
public final class ChannelExpression extends Expression {
  /** Resolves the series in a Detector; each call on a moving average creates its own window. */
  private final Function<Detector, Series> series;

  ChannelExpression(String channel) {
    Objects.requireNonNull(channel, "channel");
    this.series = detector -> detector.channel(channel);
  }

  private ChannelExpression(Function<Detector, Series> series) {
    this.series = series;
  }

  @Override
  Bound bind(Detector detector) {
    Series s = series.apply(detector);
    return () -> s.value;
  }

  /** True when the last value is greater than the previous one. */
  public Condition rising() {
    return new Condition() {
      @Override Bound bind(Detector detector) {
        Series s = series.apply(detector);
        return () -> Truth.greater(s.value, s.previous);
      }
    };
  }

  /** True when the last value is less than the previous one. */
  public Condition falling() {
    return new Condition() {
      @Override Bound bind(Detector detector) {
        Series s = series.apply(detector);
        return () -> Truth.less(s.value, s.previous);
      }
    };
  }

  /** Change per second between the last two values. */
  public Expression rate() {
    return new Expression() {
      @Override Bound bind(Detector detector) {
        return series.apply(detector)::rate;
      }
    };
  }

  /** Mean of the last {@code n} values; undefined until {@code n} values exist. */
  public ChannelExpression averageOverSamples(int n) {
    if (n < 1) throw new IllegalArgumentException("averageOverSamples needs n >= 1: " + n);
    return average(() -> new Window.OverSamples(n));
  }

  /**
   * Mean of the values from the last {@code millis} milliseconds, counted back from the latest
   * value; undefined until the values span that long.
   */
  public ChannelExpression averageOverDuration(long millis) {
    if (millis < 0) throw new IllegalArgumentException("averageOverDuration must be >= 0");
    return average(() -> new Window.OverDuration(millis));
  }

  /** See {@link #averageOverDuration(long)}. */
  public ChannelExpression averageOverDuration(Duration duration) {
    return averageOverDuration(duration.toMillis());
  }

  /** Each binding gets its own window, fed with every value of the source from the start. */
  private ChannelExpression average(Supplier<Window> newWindow) {
    return new ChannelExpression(detector -> series.apply(detector).average(newWindow.get()));
  }
}
