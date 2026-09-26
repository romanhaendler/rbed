package rbed;

/** Static factories for building Conditions; intended for static import. */
public final class Conditions {
  private Conditions() {}

  /** The current (last known) value of the named Channel. */
  public static Expression channel(String name) {
    return new Expression() {
      @Override double value(Detector d) { return d.channelValue(name); }
    };
  }
}
