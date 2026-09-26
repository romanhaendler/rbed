package rbed;

import java.util.List;
import java.util.Objects;

/** Static factories for building Conditions; intended for static import. */
public final class Conditions {
  private Conditions() {}

  /** The current (last known) value of the named Channel. */
  public static ChannelExpression channel(String name) {
    return new ChannelExpression(name);
  }

  /** True while an Event with the given name is Confirmed and has not Ended. */
  public static Condition during(String eventName) {
    Objects.requireNonNull(eventName, "eventName");
    return new Condition() {
      @Override Bound bind(Detector detector) {
        EventState event = detector.referencedEvent(eventName);
        return () -> Truth.of(event.confirmed);
      }
    };
  }

  /** True once an Event with the given name has Ended; see {@link After} for narrowing it. */
  public static After after(String eventName) {
    return new After(eventName, List.of(), Long.MAX_VALUE);
  }
}
