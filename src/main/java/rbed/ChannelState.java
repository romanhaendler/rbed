package rbed;

/** One Channel inside one Detector. */
final class ChannelState extends Series {
  final String name;
  /** A Pulse Channel's value exists only in the Sample carrying it. */
  boolean pulse;

  ChannelState(String name) {
    this.name = name;
  }

  /** Makes a Pulse Channel undefined unless the Sample at {@code timestamp} carried it. */
  void endPulse(long timestamp) {
    if (time != timestamp) value = Double.NaN;
  }
}
