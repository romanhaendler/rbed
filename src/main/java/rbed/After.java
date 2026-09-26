package rbed;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * True once an Event with the given name has Ended, optionally narrowed by {@link #unless} and
 * {@link #within}. Create with {@link Conditions#after}. See ADR-0002 for why interrupting Events
 * are named explicitly.
 */
public final class After extends Condition {
  private final String name;
  private final List<String> unless;
  private final long within;

  After(String name, List<String> unless, long within) {
    this.name = name;
    this.unless = unless;
    this.within = within;
  }

  /** No Event with any of these names may have been Confirmed since the most recent one Ended. */
  public After unless(String... names) {
    List<String> all = new ArrayList<>(unless);
    all.addAll(Arrays.asList(names));
    return new After(name, List.copyOf(all), within);
  }

  /** The most recent Event must have Ended at most {@code millis} before the current Sample. */
  public After within(long millis) {
    if (millis < 0) throw new IllegalArgumentException("within must be >= 0");
    return new After(name, unless, millis);
  }

  /** See {@link #within(long)}. */
  public After within(Duration duration) {
    return within(duration.toMillis());
  }

  @Override
  Boolean test(Detector d) {
    Long end = d.lastEnd(name);
    if (end == null || d.now() - end > within) return false;
    for (String u : unless) {
      Long confirmed = d.lastConfirmed(u);
      if (confirmed != null && confirmed > end) return false;
    }
    return true;
  }

  List<String> referencedEvents() {
    List<String> all = new ArrayList<>(unless);
    all.add(name);
    return all;
  }
}
