package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * A moving average depends only on the values currently in its window: the sum is exact (rounded
 * once), never on the order in which values entered or on values that have left.
 */
class MovingAverageExactnessTest {
  static final Channel X = Channel.named("x");
  static final double[] AWKWARD = {1e16, 1, -1e16, 1e-320, -1e-320, 4.9e-324, 0.0, -0.0, 0.1, 0.2,
      0.3, 1e300, -1e300, 1.7e308, -1.7e308, 1e-300, 3.0, 1e15, -1e15, 123456.789};

  /** The oracle: exact decimal arithmetic, one rounding for the sum, one for the division. */
  static double mean(List<Double> window) {
    BigDecimal sum = BigDecimal.ZERO;
    for (double v : window) sum = sum.add(new BigDecimal(v));
    return sum.doubleValue() / window.size();
  }

  static long bits(double d) {
    return Double.doubleToRawLongBits(d);
  }

  static double lastOf(Expression e, double[] xs, long step) {
    Detector d = Detector.builder().watch(e).build();
    for (int i = 0; i < xs.length; i++) d.push(i * step, Map.of(X, xs[i]));
    return d.value(e).orElse(Double.NaN);
  }

  static double[] concat(double[] a, double[] b) {
    double[] out = new double[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }

  @Test
  void theSameWindowGivesTheSameAverageWhateverEnteredAndLeftBefore() {
    Expression e = X.averageOverSamples(4);
    double[] tail = {0.1, 1e16, 1, 0.3};
    long expected = bits(mean(List.of(0.1, 1e16, 1.0, 0.3)));
    for (double[] prefix : new double[][] {{}, {5}, {1e16, 1, -1e16}, {1e300, -1e300, 4.9e-324},
        {0.1, 0.2, 0.3, 0.4, 0.5}, {-0.0, 0.0}, {1.7e308, 1.7e308, -1.7e308, -1.7e308, 1e-320}}) {
      assertEquals(expected, bits(lastOf(e, concat(prefix, tail), 10)), "prefix " + prefix.length);
    }
  }

  @Test
  void aWindowOverDurationDoesNotRememberWhatLeftByTime() {
    Expression e = X.averageOverDuration(30);
    double[] tail = {1.0, 1e16, -1e16, 0.5}; // 10 ms apart: 30 ms span, all older values are out
    long expected = bits(mean(List.of(1.0, 1e16, -1e16, 0.5)));
    for (double[] prefix : new double[][] {{}, {7e15, 3}, {1e16, 1, -1e16, 1e-300}}) {
      assertEquals(expected, bits(lastOf(e, concat(prefix, tail), 10)), "prefix " + prefix.length);
    }
  }

  @Test
  void cancellationAtTheLimitsOfPrecisionIsExact() {
    // 1e16 + 1 - 1e16 is 1 exactly; plain and even compensated sums can lose it
    assertEquals(1.0 / 3, lastOf(X.averageOverSamples(3), new double[] {1e16, 1, -1e16}, 1));
    assertEquals(4.9e-324 / 2,
        lastOf(X.averageOverSamples(2), new double[] {1e300, -1e300, 4.9e-324, 0.0}, 1));
    assertEquals(bits(0.0), bits(lastOf(X.averageOverSamples(2), new double[] {-0.0, -0.0}, 1)));
  }

  @Test
  void randomWindowsMatchExactArithmeticBitForBit() {
    SplittableRandom r = new SplittableRandom(42);
    for (int round = 0; round < 300; round++) {
      int n = 1 + r.nextInt(8);
      long millis = 5 + r.nextInt(40);
      Expression bySamples = X.averageOverSamples(n);
      Expression byDuration = X.averageOverDuration(millis);
      Detector d = Detector.builder().watch(bySamples, byDuration).build();
      List<Double> values = new ArrayList<>();
      List<Long> times = new ArrayList<>();
      long t = 0;
      for (int i = 0; i < 200; i++) {
        double v = r.nextInt(3) == 0 ? AWKWARD[r.nextInt(AWKWARD.length)]
            : (r.nextBoolean() ? 1 : -1) * Math.scalb(r.nextDouble(), r.nextInt(120) - 60);
        t += 1 + r.nextInt(6);
        d.push(t, Map.of(X, v));
        values.add(v);
        times.add(t);
        if (values.size() >= n) {
          double expected = mean(values.subList(values.size() - n, values.size()));
          if (Double.isFinite(expected)) {
            assertEquals(bits(expected), bits(d.value(bySamples).getAsDouble()),
                "samples " + round + "/" + i);
          }
        }
        if (t - times.get(0) >= millis) {
          int from = 0;
          while (t - times.get(from) > millis) from++;
          double expected = mean(values.subList(from, values.size()));
          if (Double.isFinite(expected)) {
            assertEquals(bits(expected), bits(d.value(byDuration).getAsDouble()),
                "duration " + round + "/" + i);
          }
        }
      }
    }
  }
}
