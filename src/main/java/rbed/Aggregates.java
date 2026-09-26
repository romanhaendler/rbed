package rbed;

/**
 * Aggregates of one Channel over an Event. {@code min}, {@code max}, {@code mean} and {@code
 * count} are over the Samples in which the Channel had a value, so they are NaN when {@code count}
 * is 0. {@code integral} (value x seconds) and {@code timeWeightedMean} weight each value by how
 * long it held (until the next Sample, time advance or the Event's end), from the Channel's first
 * value during the Event, including a value it already held when the Event started on a time
 * advance. They are NaN if the Channel had no value during the Event; the time-weighted mean is
 * also NaN for a zero span.
 */
public record Aggregates(
    double min, double max, double mean, long count, double timeWeightedMean, double integral) {
  static final Aggregates EMPTY =
      new Aggregates(Double.NaN, Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN);
}
