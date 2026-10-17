package io.github.romanhaendler.rbed;

import java.util.List;
import java.util.Objects;

/** Static factories for building Conditions; intended for static import. */
public final class Conditions {
  private Conditions() {}

  /** True while an Event of the given Rule is Confirmed and has not Ended. */
  public static Condition during(Rule rule) {
    Objects.requireNonNull(rule, "rule");
    return new Condition("during(" + rule.name() + ")") {
      @Override Bound bind(Detector detector) {
        EventState event = detector.referencedEvent(rule);
        return () -> Truth.of(event.confirmed);
      }
    };
  }

  /** True once an Event of the given Rule has Ended; see {@link After} for narrowing it. */
  public static After after(Rule rule) {
    return new After(rule, List.of(), Long.MAX_VALUE);
  }

  /**
   * The number of Events of the given Rule Confirmed since the Detector started, History
   * included; narrow it with {@link EventCount#within}.
   */
  public static EventCount count(Rule rule) {
    return new EventCount(rule, -1);
  }

  /**
   * The summed duration in milliseconds of all Events of the given Rule since the Detector
   * started, History included: Ended ones in full, plus a Confirmed running one up to now.
   */
  public static Expression totalDuration(Rule rule) {
    Objects.requireNonNull(rule, "rule");
    return new Expression("totalDuration(" + rule.name() + ")") {
      @Override Bound bind(Detector detector) {
        EventState events = detector.referencedEvent(rule);
        return () -> events.totalDuration(detector.now());
      }
    };
  }

  /**
   * Values of the most recent Confirmed Event of the given Rule, running or Ended, such as
   * {@code latest(loadLifted).max(weight)}.
   */
  public static LatestEvent latest(Rule rule) {
    return new LatestEvent(rule);
  }
}
