package rbed;

/** One Channel inside one Detector. */
final class ChannelState extends ValueTrack {
  final String name;
  /** Whether the Sample being applied carried this Channel; only maintained for pulses. */
  boolean carried;
  private boolean absent;

  ChannelState(String name) {
    this.name = name;
  }

  @Override
  void update(long t, double v) {
    super.update(t, v);
    carried = true;
    absent = false;
  }

  @Override
  boolean absent() {
    return absent;
  }

  /** A Pulse Channel that the current moment does not carry: its value is absent. */
  void pulseAbsent() {
    value = Double.NaN;
    absent = true;
  }
}
