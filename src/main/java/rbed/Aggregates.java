package rbed;

/**
 * Minimum, maximum and mean of one Channel over the Samples of an Event. {@code count} is the
 * number of Samples in which the Channel had a value; when it is 0, the other fields are NaN.
 */
public record Aggregates(double min, double max, double mean, long count) {
  static final Aggregates EMPTY = new Aggregates(Double.NaN, Double.NaN, Double.NaN, 0);
}
