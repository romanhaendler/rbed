package io.github.romanhaendler.rbed;

import java.io.IOException;
import java.util.Arrays;

/**
 * The exact sum of the values added and not removed again, kept as one wide fixed-point integer
 * (a superaccumulator): every finite double is a whole multiple of 2^-1074, so a sum of doubles
 * is a whole number of those units. The integer is held in {@value #LIMBS} limbs of 32 bits each,
 * in {@code long}s, so adding or removing a value is integer arithmetic on at most three limbs and
 * never rounds. Limbs are not normalised while adding (they are signed sums of 32-bit pieces and
 * cannot overflow with fewer than 2^31 values in the sum), which makes the limbs themselves a
 * function of the multiset of values alone: the order and the values that left cannot show.
 * {@link #value()} converts to the nearest double (ties to even), infinite exactly when IEEE
 * rounding of the true sum overflows. Nothing is ever allocated after construction.
 */
final class ExactSum {
  /** 2098 bits cover every double (2^-1074 .. 2^1024); the rest is room for the sum to grow. */
  private static final int LIMBS = 68;
  private static final long MASK = 0xFFFFFFFFL;

  private final long[] limbs = new long[LIMBS];
  private int lo = LIMBS; // limbs outside lo..hi are zero; only speeds up value()
  private int hi = -1;
  private final long[] work = new long[LIMBS + 3]; // scratch for value(), not state

  void add(double value) {
    addSigned(value, 1);
  }

  /** Takes back a value added earlier; the sum is as if it had never been added. */
  void remove(double value) {
    addSigned(value, -1);
  }

  private void addSigned(double value, long sign) {
    long bits = Double.doubleToRawLongBits(value);
    int exp = (int) (bits >>> 52) & 0x7FF;
    long mant = bits & ((1L << 52) - 1);
    if (exp != 0) mant |= 1L << 52;
    if (mant == 0) return;
    if (bits < 0) sign = -sign;
    int position = exp == 0 ? 0 : exp - 1; // bit of the lowest mantissa bit, in units of 2^-1074
    int i = position >>> 5;
    int shift = position & 31;
    long low = (mant & ((1L << (32 - shift)) - 1)) << shift;
    long rest = mant >>> (32 - shift);
    lo = Math.min(lo, i);
    hi = Math.max(hi, i + 2);
    limbs[i] += sign * low;
    limbs[i + 1] += sign * (rest & MASK);
    limbs[i + 2] += sign * (rest >>> 32);
  }

  /** How many limbs {@link #value()} walks; only for tests. */
  int touchedLimbs() {
    return Math.max(0, hi - lo + 1);
  }

  /** The exact sum rounded to the nearest double, ties to even; infinite on overflow. */
  double value() {
    while (lo <= hi && limbs[lo] == 0) lo++; // keep the range tight once extremes have left
    while (hi >= lo && limbs[hi] == 0) hi--;
    if (hi < lo) return 0.0;
    long[] w = work;
    int from = Math.max(0, lo - 3); // a rounding window reaches two limbs below and above
    Arrays.fill(w, from, hi + 5, 0); // nothing outside is ever read
    long carry = 0; // only the limbs ever touched are non-zero: lo..hi
    for (int i = lo; i <= hi; i++) {
      long t = limbs[i] + carry;
      w[i] = t & MASK;
      carry = t >> 32;
    }
    boolean negative = carry < 0;
    if (negative) { // negate: -(low + carry * 2^(32 * (hi + 1))) with the magnitude in every limb
      long borrow = 0;
      for (int i = lo; i <= hi; i++) {
        long t = borrow - w[i];
        w[i] = t & MASK;
        borrow = t >> 32;
      }
      carry = borrow - carry;
    }
    w[hi + 1] = carry; // below 2^32 for fewer than 2^31 values
    int top = hi + 2;
    while (top >= 0 && w[top] == 0) top--;
    if (top < 0) return 0.0;
    int highest = top * 32 + 63 - Long.numberOfLeadingZeros(w[top]); // bit index of the leading 1
    if (highest - 1074 > 1023) return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    int low = Math.max(highest - 52, 0); // lowest bit kept; below 2^-1022 everything is kept
    int i = low >>> 5;
    int s = low & 31;
    long window = w[i] | (w[i + 1] << 32);
    long mant = (s == 0 ? window : (window >>> s) | (w[i + 2] << (64 - s))) & ((1L << 53) - 1);
    if (low > 0) {
      int r = low - 1;
      boolean roundBit = ((w[r >>> 5] >>> (r & 31)) & 1) != 0;
      if (roundBit) {
        boolean sticky = (w[r >>> 5] & ((1L << (r & 31)) - 1)) != 0;
        for (int k = (r >>> 5) - 1; k >= from && !sticky; k--) sticky = w[k] != 0;
        if (sticky || (mant & 1) != 0) mant++;
      }
    }
    // low > 0: mant has its leading 1 at bit 52 (or is 2^53 after rounding up, which carries into
    // the exponent, up to the bits of infinity); low == 0: mant is the bit pattern itself
    double magnitude = Double.longBitsToDouble(
        low == 0 ? mant : ((long) (low + 1) << 52) + (mant - (1L << 52)));
    return negative ? -magnitude : magnitude;
  }

  /** The non-zero limbs as (index, value) pairs; the limbs are a function of the values alone. */
  void save(StateWriter w) {
    int count = 0;
    for (long l : limbs) if (l != 0) count++;
    w.int32(count);
    for (int i = 0; i < LIMBS; i++) {
      if (limbs[i] == 0) continue;
      w.int32(i);
      w.int64(limbs[i]);
    }
  }

  void load(StateReader r) throws IOException {
    int count = r.int32();
    if (count < 0 || count > LIMBS) throw new IOException("sum");
    Arrays.fill(limbs, 0);
    lo = LIMBS;
    hi = -1;
    int previous = -1;
    for (int k = 0; k < count; k++) {
      int i = r.int32();
      if (i <= previous || i >= LIMBS - 2) throw new IOException("sum");
      previous = i;
      limbs[i] = r.int64();
      lo = Math.min(lo, i);
      hi = Math.max(hi, i);
    }
  }
}
