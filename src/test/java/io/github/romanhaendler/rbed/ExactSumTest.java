package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The sum behind moving averages: exact over the whole range of doubles, rounded once. */
class ExactSumTest {
  static long bits(double d) {
    return Double.doubleToRawLongBits(d);
  }

  /** The oracle: exact decimal arithmetic, IEEE rounding to nearest even (infinite on overflow). */
  static double oracle(List<Double> values) {
    BigDecimal sum = BigDecimal.ZERO;
    for (double v : values) sum = sum.add(new BigDecimal(v));
    return sum.doubleValue() + 0.0;
  }

  static double sum(double... values) {
    ExactSum s = new ExactSum();
    for (double v : values) s.add(v);
    return s.value();
  }

  static double random(SplittableRandom r) {
    double sign = r.nextBoolean() ? 1 : -1;
    switch (r.nextInt(6)) {
      case 0: return sign * Double.MAX_VALUE * r.nextDouble();
      case 1: return sign * Math.scalb(r.nextDouble(), -1074 + r.nextInt(60));
      case 2: return sign * Double.longBitsToDouble(r.nextLong() & 0x7FEFFFFFFFFFFFFFL);
      case 3: return sign * Math.scalb(1.0, r.nextInt(2098) - 1074);
      case 4: return sign * Double.MAX_VALUE;
      default: return sign * Math.scalb(r.nextDouble(), r.nextInt(2000) - 1000);
    }
  }

  @Test
  void theSumAtTheOverflowEdgeIsExact() {
    // the true sum is MAX_VALUE; MAX_VALUE + 2^970 alone would round to infinity
    assertEquals(Double.MAX_VALUE, sum(Double.MAX_VALUE, 0x1p970, -1.0));
    assertEquals(Double.MAX_VALUE, sum(Double.MAX_VALUE, 0x1p970, -0x1p970));
    assertEquals(Double.MAX_VALUE, sum(0x1p970, Double.MAX_VALUE, -0x1p970));
  }

  @Test
  void overflowHappensExactlyWhereRoundingOverflows() {
    double halfUlp = 0x1p970; // MAX_VALUE + half an ulp is the first sum that rounds to infinity
    assertEquals(Double.POSITIVE_INFINITY, sum(Double.MAX_VALUE, halfUlp));
    assertEquals(Double.MAX_VALUE, sum(Double.MAX_VALUE, Math.nextDown(halfUlp)));
    assertEquals(Double.NEGATIVE_INFINITY, sum(-Double.MAX_VALUE, -halfUlp));
    assertEquals(Double.MAX_VALUE, sum(Double.MAX_VALUE, halfUlp, -1e290));
    assertEquals(bits(0.0),
        bits(sum(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE)));
  }

  @Test
  void randomSumsMatchTheOracleBitForBit() {
    SplittableRandom r = new SplittableRandom(7);
    for (int round = 0; round < 20_000; round++) {
      List<Double> values = new ArrayList<>();
      ExactSum s = new ExactSum();
      int n = 1 + r.nextInt(8);
      for (int i = 0; i < n; i++) {
        double v = random(r);
        values.add(v);
        s.add(v);
      }
      assertEquals(bits(oracle(values)), bits(s.value()), "round " + round + " " + values);
      int removed = r.nextInt(n); // take some back: still the oracle of what is left
      for (int i = 0; i < removed; i++) s.remove(values.remove(r.nextInt(values.size())));
      assertEquals(bits(oracle(values)), bits(s.value()), "after removal, round " + round);
    }
  }

  @Test
  void theStateDependsOnlyOnTheValuesInTheSum() {
    SplittableRandom r = new SplittableRandom(11);
    for (int round = 0; round < 2_000; round++) {
      double[] keep = new double[1 + r.nextInt(4)];
      for (int i = 0; i < keep.length; i++) keep[i] = random(r);
      ExactSum fresh = new ExactSum();
      for (double v : keep) fresh.add(v);
      ExactSum worn = new ExactSum();
      List<Double> passed = new ArrayList<>();
      for (int i = 0; i < 6; i++) passed.add(random(r));
      for (double v : passed) worn.add(v);
      for (double v : keep) worn.add(v);
      for (double v : passed) worn.remove(v);
      assertEquals(bits(fresh.value()), bits(worn.value()), "round " + round);
      StateWriter a = new StateWriter();
      StateWriter b = new StateWriter();
      fresh.save(a);
      worn.save(b);
      assertArrayEquals(a.toByteArray(), b.toByteArray(), "saved words, round " + round);
    }
  }

  @Test
  void aMovingAverageAfterHugeValuesIsTheSameInAReplay() {
    // regression: the replay of the Samples from the Replay Point gave another average
    Channel x = Channel.named("x");
    double[] seq = {3.2733906078961426E150, 8.98846567431158E307, 4.5097919418014424E282,
        -1.7976931348623157E308, -9.9792015476736E291, 1.948708047399462E93};
    Expression avg = x.averageOverSamples(4);
    Detector d = Detector.builder().watch(avg).build();
    for (int i = 0; i < seq.length; i++) d.push(i + 1, Map.of(x, seq[i]));
    long from = d.replayPoint().getAsLong();
    Detector replay = Detector.builder().watch(avg).build();
    for (int i = 0; i < seq.length; i++) if (i + 1 >= from) replay.push(i + 1, Map.of(x, seq[i]));
    assertEquals(bits(d.value(avg).getAsDouble()), bits(replay.value(avg).getAsDouble()));
  }

  @Test
  void manyValuesCarryBeyondTheLimbsTheyTouched() {
    // thousands of equal values overflow the top 32-bit limb they started in
    for (double v : new double[] {1e300, -1e300, 0x1p-1074, 1.5, Double.MAX_VALUE / 1e4}) {
      for (int count : new int[] {2_049, 5_000, 70_000}) {
        ExactSum s = new ExactSum();
        List<Double> all = new ArrayList<>();
        for (int i = 0; i < count; i++) {
          s.add(v);
          all.add(v);
        }
        assertEquals(bits(oracle(all)), bits(s.value()), v + " x " + count);
        s.add(-v);
        all.remove(0);
        assertEquals(bits(oracle(all)), bits(s.value()), v + " x " + count + " - 1");
      }
    }
  }

  private static StateReader reader(StateWriter w) {
    byte[] bytes = w.toByteArray();
    return new StateReader(bytes, bytes.length);
  }

  @Test
  void aSavedSumLoadsBackAndReplacesWhatWasThere() throws IOException {
    ExactSum saved = new ExactSum();
    saved.add(1e300);
    saved.add(-3.5);
    StateWriter w = new StateWriter();
    saved.save(w);
    ExactSum loaded = new ExactSum();
    loaded.add(7e200); // must not survive the load
    loaded.add(1e-300);
    loaded.load(reader(w));
    assertEquals(bits(saved.value()), bits(loaded.value()));
    loaded.remove(1e300);
    loaded.remove(-3.5);
    assertEquals(bits(0.0), bits(loaded.value()));
  }

  @Test
  void anImplausibleSavedSumIsRejected() {
    // count, then (index, value) pairs
    long[][] bad = {{-1}, {69}, {1, 70, 0}, {1, 66, 0}, {1, -1, 0}, {2, 5, 5, 5, 6},
        {2, 6, 5, 5, 5}};
    for (long[] b : bad) {
      StateWriter w = new StateWriter();
      w.int32((int) b[0]);
      for (int k = 1; k + 1 < b.length; k += 2) {
        w.int32((int) b[k]);
        w.int64(b[k + 1]);
      }
      assertThrows(IOException.class, () -> new ExactSum().load(reader(w)),
          java.util.Arrays.toString(b));
    }
    StateWriter ok = new StateWriter(); // control: ascending indexes below the top are fine
    ok.int32(2);
    ok.int32(3);
    ok.int64(5);
    ok.int32(65);
    ok.int64(6);
    try {
      new ExactSum().load(reader(ok));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
