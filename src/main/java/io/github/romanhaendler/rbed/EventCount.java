package io.github.romanhaendler.rbed;

import java.time.Duration;
import java.util.Objects;

/**
 * The number of Events of a given Rule that were Confirmed, History included: since the
 * Detector started, or {@link #within} a sliding window. Create with {@link Conditions#count}.
 */
public final class EventCount extends Expression {
  private final Rule rule;
  private final long window;

  EventCount(Rule rule, long window) {
    super("count(" + rule.name() + ")" + (window < 0 ? "" : ".within(" + window + ")"),
        Key.node("count", Key.name(rule.name()), Key.number(window)));
    this.rule = Objects.requireNonNull(rule, "rule");
    this.window = window;
  }

  /**
   * Counts only Events Confirmed at most {@code millis} before the current moment. Once the
   * Retention Limit (see {@link Detector.Builder#retentionLimit}) drops timestamps, the count is
   * min(true count, limit): a Condition with a threshold at or above the limit (e.g.
   * {@code .above(limit)}) can then never hold. {@link Detector#droppedValues} and the logged
   * warning signal this. The timestamp buffer keeps its largest size after a burst (at most the
   * limit).
   */
  public Expression within(long millis) {
    if (millis < 0) throw new IllegalArgumentException("within must be >= 0: " + millis);
    return new EventCount(rule, millis);
  }

  /** See {@link #within(long)}. */
  public Expression within(Duration duration) {
    return within(duration.toMillis());
  }

  @Override
  Bound bind(Detector detector) {
    EventState events = detector.referencedEvent(rule);
    if (window < 0) return () -> events.count;
    detector.windowMillis(window);
    events.countWithin(window);
    Timestamps.Counter counter = events.counter(window);
    return () -> {
      long now = detector.now();
      events.dropExpired(now);
      return counter.countWithin(now);
    };
  }
}
