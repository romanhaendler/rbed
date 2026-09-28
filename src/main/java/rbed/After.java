package rbed;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * True once an Event of the given Rule has Ended, optionally narrowed by {@link #unless} and
 * {@link #within}. Create with {@link Conditions#after}. See ADR-0002 for why interrupting Events
 * are named explicitly.
 */
public final class After extends Condition {
  private final Rule rule;
  private final List<Rule> unless;
  private final long within;

  After(Rule rule, List<Rule> unless, long within) {
    this.rule = Objects.requireNonNull(rule, "rule");
    this.unless = unless;
    this.within = within;
  }

  /** No Event of any of these Rules may have been Confirmed since the most recent one Ended. */
  public After unless(Rule... rules) {
    List<Rule> all = new ArrayList<>(unless);
    for (Rule r : rules) all.add(Objects.requireNonNull(r, "rule"));
    return new After(rule, List.copyOf(all), within);
  }

  /** The most recent Event must have Ended at most {@code millis} before the current Sample. */
  public After within(long millis) {
    if (millis < 0) throw new IllegalArgumentException("within must be >= 0: " + millis);
    return new After(rule, unless, millis);
  }

  /** See {@link #within(long)}. */
  public After within(Duration duration) {
    return within(duration.toMillis());
  }

  @Override
  Bound bind(Detector detector) {
    EventState predecessor = detector.referencedEvent(rule);
    EventState[] interrupters =
        unless.stream().map(detector::referencedEvent).toArray(EventState[]::new);
    return () -> {
      if (!predecessor.hasEnded || detector.now() - predecessor.lastEnd > within) {
        return Truth.FALSE;
      }
      for (EventState interrupter : interrupters) {
        if (interrupter.hasBeenConfirmed && interrupter.lastConfirmed > predecessor.lastEnd) {
          return Truth.FALSE;
        }
      }
      return Truth.TRUE;
    };
  }
}
