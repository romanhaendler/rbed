package rbed;

import java.util.List;
import java.util.Objects;

/** Static factories for building Conditions; intended for static import. */
public final class Conditions {
  private Conditions() {}

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

  /**
   * The number of Events with the given name Confirmed since the Detector started, History
   * included; narrow it with {@link EventCount#within}.
   */
  public static EventCount count(String eventName) {
    return new EventCount(eventName, -1);
  }

  /**
   * The summed duration in milliseconds of all Events with the given name since the Detector
   * started, History included: Ended ones in full, plus a Confirmed running one up to now.
   */
  public static Expression totalDuration(String eventName) {
    Objects.requireNonNull(eventName, "eventName");
    return new Expression() {
      @Override Bound bind(Detector detector) {
        EventState events = detector.referencedEvent(eventName);
        return () -> events.totalDuration(detector.now());
      }
    };
  }

  /**
   * Values of the most recent Confirmed Event with the given name, running or Ended, such as
   * {@code latest("LoadLifted").max("weight")}.
   */
  public static LatestEvent latest(String eventName) {
    return new LatestEvent(eventName);
  }
}
