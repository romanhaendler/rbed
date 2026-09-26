package rbed;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The current value of a Channel, or of a moving average derived from one, plus Expressions over
 * how it developed. Functions of the past use the track's own updates (a Channel's values, not the
 * Samples in which it was absent) and are undefined until enough of them exist.
 */
public final class ChannelExpression extends Expression {
  /** Resolves the track in a Detector; each call on a moving average creates its own window. */
  private final Function<Detector, ValueTrack> track;

  ChannelExpression(String channel) {
    Objects.requireNonNull(channel, "channel");
    this.track = detector -> detector.referencedChannel(channel);
  }

  private ChannelExpression(Function<Detector, ValueTrack> track) {
    this.track = track;
  }

  @Override
  Bound bind(Detector detector) {
    ValueTrack t = track.apply(detector);
    return new Bound() {
      @Override public double value() {
        return t.value;
      }

      @Override public boolean absent() {
        return t.absent();
      }
    };
  }

  /** True when the latest value is greater than the previous one. */
  public Condition rising() {
    return trend(true);
  }

  /** True when the latest value is less than the previous one. */
  public Condition falling() {
    return trend(false);
  }

  /** Change per second between the last two values. */
  public Expression rate() {
    return new Expression() {
      @Override Bound bind(Detector detector) {
        return track.apply(detector)::rate;
      }
    };
  }

  /**
   * Milliseconds since the latest value, at the current Sample or time advance; undefined before
   * the first value. Together with {@link Detector#advanceTo} this detects silent sensors.
   */
  public Expression age() {
    return new Expression() {
      @Override Bound bind(Detector detector) {
        ValueTrack t = track.apply(detector);
        return () -> t.age(detector.now());
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

  private Condition trend(boolean rising) {
    return new Condition() {
      @Override Bound bind(Detector detector) {
        ValueTrack t = track.apply(detector);
        return rising
            ? () -> t.absent() ? Truth.FALSE : Truth.greater(t.value, t.previous)
            : () -> t.absent() ? Truth.FALSE : Truth.less(t.value, t.previous);
      }
    };
  }

  /** Each binding gets its own window, fed with every value of the source from the start. */
  private ChannelExpression average(Supplier<Window> newWindow) {
    return new ChannelExpression(detector -> track.apply(detector).average(newWindow.get()));
  }
}
