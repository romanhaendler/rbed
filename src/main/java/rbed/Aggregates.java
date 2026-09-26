package rbed;

/**
 * Statistics of one Channel over an Event. {@code min}, {@code max}, {@code mean} and {@code count}
 * are over the Samples in which the Channel had a value. {@code integral} (value x seconds) and
 * {@code timeWeightedMean} weight each value by how long it held (until the next Sample, time
 * advance or the Event's end), from the Channel's first value in the Event; the time-weighted mean
 * is NaN for a zero span. When {@code count} is 0, all other fields are NaN.
 */
public record Aggregates(
    double min, double max, double mean, long count, double timeWeightedMean, double integral) {
  static final Aggregates EMPTY =
      new Aggregates(Double.NaN, Double.NaN, Double.NaN, 0, Double.NaN, Double.NaN);
}
