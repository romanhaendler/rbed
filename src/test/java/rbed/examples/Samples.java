package rbed.examples;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;
import rbed.Event;
import rbed.Sample;

/** Helpers that keep the examples about Rules rather than about generating data. */
final class Samples {
  static final long SECOND = 1_000;
  static final long MINUTE = 60 * SECOND;
  static final long HOUR = 60 * MINUTE;
  static final long DAY = 24 * HOUR;

  private Samples() {}

  /**
   * Samples every {@code step} from {@code from} (inclusive) to {@code to} (exclusive), all in the
   * given unit; {@code values} receives the time in that unit.
   */
  static List<Sample> every(double step, double from, double to, long unit,
      DoubleFunction<Map<String, Double>> values) {
    List<Sample> samples = new ArrayList<>();
    for (long i = 0; from + i * step < to; i++) {
      double t = from + i * step;
      samples.add(new Sample(Math.round(t * unit), values.apply(t)));
    }
    return samples;
  }

  /** "Name start..end" with times in the given unit, plus Truncated flags. */
  static String describe(Event e, long unit) {
    StringBuilder s = new StringBuilder(e.name()).append(' ').append(format(e.start(), unit))
        .append("..").append(e.end().isPresent() ? format(e.end().getAsLong(), unit) : "?");
    if (e.truncatedStart()) s.append(" truncatedStart");
    if (e.truncatedEnd()) s.append(" truncatedEnd");
    return s.toString();
  }

  static List<String> describe(List<Event> events, long unit) {
    return events.stream().map(e -> describe(e, unit)).toList();
  }

  private static String format(long millis, long unit) {
    double t = (double) millis / unit;
    return t == Math.rint(t) ? Long.toString((long) t) : Double.toString(t);
  }
}
