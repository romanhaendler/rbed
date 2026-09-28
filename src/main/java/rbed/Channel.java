package rbed;

import java.util.Objects;
import java.util.function.Function;

/**
 * A named signal within a time series, such as "weight". Create each Channel once and use it in
 * Conditions, when pushing values and when reading Aggregates. Channels are equal by name, so a
 * Channel created from external data (e.g. a CSV header) matches the one the Rules use.
 */
public final class Channel extends ChannelExpression {
  private final String name;
  private final boolean pulse;

  private Channel(String name, boolean pulse) {
    super(track(name, pulse));
    this.name = name;
    this.pulse = pulse;
  }

  /** A Channel whose value holds until the next value arrives. */
  public static Channel named(String name) {
    return new Channel(name, false);
  }

  /**
   * A Pulse Channel, such as one reporting deployments: its value exists only in the Sample that
   * carries it. In other Samples it is absent: comparisons on it (and {@code rising}/{@code
   * falling}) are false, so {@code not()} of them is true, while computing with it is undefined.
   * {@code age()} gives the time since the last pulse.
   */
  public static Channel pulse(String name) {
    return new Channel(name, true);
  }

  public String name() {
    return name;
  }

  /** This Channel's state in a Detector, without recording it as a lookup of the Rule. */
  ChannelState state(Detector detector) {
    return detector.channel(name, pulse);
  }

  private static Function<Detector, ValueTrack> track(String name, boolean pulse) {
    if (Objects.requireNonNull(name, "name").isEmpty()) {
      throw new IllegalArgumentException("Channel name is empty");
    }
    return detector -> detector.referencedChannel(name, pulse);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Channel c && c.name.equals(name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return name;
  }
}
