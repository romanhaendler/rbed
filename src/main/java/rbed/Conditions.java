package rbed;

/** Static factories for building Conditions; intended for static import. */
public final class Conditions {
  private Conditions() {}

  /** The current (last known) value of the named Channel. */
  public static ChannelExpression channel(String name) {
    return new ChannelExpression(name);
  }

  /** True while an Event with the given name is Confirmed and has not Ended. */
  public static Condition during(String eventName) {
    return new During(eventName);
  }

  /** True once an Event with the given name has Ended; see {@link After} for narrowing it. */
  public static After after(String eventName) {
    return new After(eventName, java.util.List.of(), Long.MAX_VALUE);
  }
}
