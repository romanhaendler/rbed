package rbed;

import java.time.Duration;
import java.util.Objects;

/**
 * The number of Events with a given name that were Confirmed, History included: since the
 * Detector started, or {@link #within} a sliding window. Create with {@link Conditions#count}.
 */
public final class EventCount extends Expression {
  private final String name;
  private final long window;

  EventCount(String name, long window) {
    this.name = Objects.requireNonNull(name, "eventName");
    this.window = window;
  }

  /** Counts only Events Confirmed at most {@code millis} before the current moment. */
  public Expression within(long millis) {
    if (millis < 0) throw new IllegalArgumentException("within must be >= 0: " + millis);
    return new EventCount(name, millis);
  }

  /** See {@link #within(long)}. */
  public Expression within(Duration duration) {
    return within(duration.toMillis());
  }

  @Override
  Bound bind(Detector detector) {
    EventState events = detector.referencedEvent(name);
    if (window < 0) return () -> events.count;
    events.countWithin(window);
    return () -> events.confirmedWithin(detector.now(), window);
  }
}
