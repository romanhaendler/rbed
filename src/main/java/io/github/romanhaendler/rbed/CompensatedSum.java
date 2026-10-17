package io.github.romanhaendler.rbed;

/**
 * A running sum with Neumaier compensation: adding and later subtracting a huge value does not
 * wipe out the small values added in between (plain {@code 1e16 + 1 - 1e16} gives 0, this gives 1).
 */
final class CompensatedSum {
  private double sum;
  private double compensation;

  void add(double value) {
    double t = sum + value;
    compensation += Math.abs(sum) >= Math.abs(value) ? (sum - t) + value : (value - t) + sum;
    sum = t;
  }

  double value() {
    return sum + compensation;
  }

  void save(StateWriter w) {
    w.float64(sum);
    w.float64(compensation);
  }

  void load(StateReader r) throws java.io.IOException {
    sum = r.float64();
    compensation = r.float64();
  }

  void reset() {
    sum = 0;
    compensation = 0;
  }
}
