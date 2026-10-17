package io.github.romanhaendler.rbed;

/** One Channel inside one Detector. */
final class ChannelState extends ValueTrack {
  /** The Channel as first referenced by a Rule. */
  final Channel channel;
  /** Whether the Sample being applied carried this Channel; only maintained for pulses. */
  boolean carried;
  private boolean absent;

  ChannelState(Channel channel) {
    this.channel = channel;
  }

  void update(long t, double v) {
    super.update(t, v, t); // a Channel's value needs only its own Sample
    carried = true;
    absent = false;
  }

  @Override
  void save(StateWriter w) {
    super.save(w);
    w.bool(absent);
  }

  @Override
  void load(StateReader r) throws java.io.IOException {
    super.load(r);
    absent = r.bool();
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
