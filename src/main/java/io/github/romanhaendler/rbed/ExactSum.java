package io.github.romanhaendler.rbed;

import java.io.IOException;
import java.util.Arrays;

/**
 * The exact sum of the values added and not removed again, kept as a short list of non-overlapping
 * partial sums (Shewchuk's expansion, as in Python's {@code math.fsum}). Adding a value and later
 * adding its negation restores the previous state exactly, so {@link #value()} depends only on
 * the values currently in the sum, never on their order or on the ones that left: it is the exact
 * sum rounded once. Values of magnitude 2^500 and above are kept apart, scaled by 2^-600, so
 * that no partial sum can overflow before the true sum does. Adding allocates nothing unless the
 * list outgrows its array, which takes values over many orders of magnitude.
 */
final class ExactSum {
  private static final double LARGE = 0x1p500;
  private static final double DOWN = 0x1p-600;
  private static final double UP = 0x1p600;

  /** Non-overlapping partial sums, smallest first. */
  private static final class Partials {
    double[] p = new double[8];
    int n;

    void add(double x) {
      int i = 0;
      for (int j = 0; j < n; j++) {
        double y = p[j];
        if (Math.abs(x) < Math.abs(y)) {
          double swap = x;
          x = y;
          y = swap;
        }
        double hi = x + y;
        double lo = y - (hi - x);
        if (lo != 0) p[i++] = lo;
        x = hi;
      }
      if (i == p.length) p = Arrays.copyOf(p, i * 2);
      p[i++] = x;
      n = i;
    }

    /** The sum rounded to nearest, ties to even. */
    double round() {
      int k = n;
      if (k == 0) return 0;
      double hi = p[--k];
      double lo = 0;
      while (k > 0) {
        double x = hi;
        double y = p[--k];
        hi = x + y;
        lo = y - (hi - x);
        if (lo != 0) break;
      }
      if (k > 0 && (lo < 0 && p[k - 1] < 0 || lo > 0 && p[k - 1] > 0)) {
        double y = lo * 2;
        double x = hi + y;
        if (y == x - hi) hi = x;
      }
      return hi + 0.0; // -0.0 becomes 0.0: the sign of an empty or cancelled sum is not history
    }

    void save(StateWriter w) {
      w.float64s(Arrays.copyOf(p, n));
    }

    void load(StateReader r) throws IOException {
      double[] loaded = r.float64s();
      p = Arrays.copyOf(loaded, Math.max(8, loaded.length));
      n = loaded.length;
    }
  }

  private final Partials small = new Partials();
  private final Partials large = new Partials();

  void add(double value) {
    if (Math.abs(value) >= LARGE) large.add(value * DOWN);
    else small.add(value);
  }

  /** Takes back a value added earlier; the sum is as if it had never been added. */
  void remove(double value) {
    add(-value);
  }

  /** The exact sum, rounded once; infinite if it exceeds the range of a double. */
  double value() {
    if (large.n == 0) return small.round();
    Partials all = new Partials(); // only with values of magnitude 2^500 and above
    all.p = Arrays.copyOf(small.p, small.p.length);
    all.n = small.n;
    for (int i = 0; i < large.n; i++) {
      double up = large.p[i] * UP;
      if (Double.isInfinite(up)) return up;
      all.add(up);
    }
    return all.round();
  }

  void save(StateWriter w) {
    small.save(w);
    large.save(w);
  }

  void load(StateReader r) throws IOException {
    small.load(r);
    large.load(r);
  }
}
