package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

/**
 * The checks that guard against a Snapshot whose bytes are intact (the CRC-32 matches) but whose
 * content is not what a Detector could have written. The CRC makes these unreachable by damage, so
 * they are tested on the state classes themselves with hand-made streams, and through
 * {@code restore} with a Snapshot whose CRC is recomputed. Part of the teeth audit (ticket 07).
 */
class SnapshotValidationTest {
  private static final Channel A = Channel.named("a");

  private static Detector detector(int limit) {
    return Detector.builder().retentionLimit(limit).rules(Rule.named("R").when(A.above(1))).build();
  }

  private static StateReader reader(StateWriter w) {
    byte[] bytes = w.toByteArray();
    return new StateReader(bytes, bytes.length);
  }

  private static StateWriter writer(Consumer<StateWriter> body) {
    StateWriter w = new StateWriter();
    body.accept(w);
    return w;
  }

  @Test
  void aRuleStateWithAnUnknownStateOrdinalIsRejected() throws Exception {
    Detector d = detector(10);
    java.lang.reflect.Field f = Detector.class.getDeclaredField("rules");
    f.setAccessible(true);
    RuleState rule = ((RuleState[]) f.get(d))[0];
    StateWriter w = new StateWriter();
    rule.save(w);
    byte[] ok = w.toByteArray();
    rule.load(new StateReader(ok, ok.length)); // control: the intact state loads
    for (int bad : new int[] {3, -1, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
      byte[] bytes = ok.clone();
      bytes[0] = (byte) (bad >>> 24);
      bytes[1] = (byte) (bad >>> 16);
      bytes[2] = (byte) (bad >>> 8);
      bytes[3] = (byte) bad;
      assertThrows(IOException.class, () -> rule.load(new StateReader(bytes, bytes.length)),
          "ordinal " + bad);
    }
  }

  @Test
  void moreRunsThanTheDetectorKeepsAreRejected() throws IOException {
    Detector d = detector(10);
    StateWriter w = new StateWriter();
    d.saveState(w);
    byte[] ok = w.toByteArray();
    d.loadState(new StateReader(ok, ok.length)); // control: the intact state loads
    // started, now, closed, lastSample, samples, retained, dropped, hasHistory, latestHistoryEnd
    int at = 1 + 8 + 1 + 8 + 8 + 8 + 8 + 1 + 8;
    byte[] bytes = java.util.Arrays.copyOf(ok, ok.length + 65 * 16); // room for all 65 pairs
    bytes[at + 3] = 65; // 65 runs, but at most 64 are kept
    assertThrows(IOException.class, () -> d.loadState(new StateReader(bytes, bytes.length)));
  }

  @Test
  void anAccumulatorWhoseArraysDiffInLengthIsRejected() {
    for (int odd = 0; odd < 7; odd++) {
      final int which = odd;
      StateWriter w = writer(s -> {
        s.float64s(new double[which == 0 ? 2 : 1]);
        s.float64s(new double[which == 1 ? 2 : 1]);
        s.float64s(new double[which == 2 ? 2 : 1]);
        s.int64s(new long[1]);
        s.float64s(new double[which == 3 ? 2 : 1]);
        s.float64s(new double[which == 4 ? 2 : 1]);
        s.bools(new boolean[which == 5 ? 2 : 1]);
        s.int64s(new long[which == 6 ? 2 : 1]);
        s.int64(0);
        s.int64(0);
        s.bool(true);
      });
      assertThrows(IOException.class, () -> new Accumulator().load(reader(w)), "array " + which);
    }
    // control: equal lengths load
    StateWriter w = writer(s -> {
      s.float64s(new double[1]);
      s.float64s(new double[1]);
      s.float64s(new double[1]);
      s.int64s(new long[1]);
      s.float64s(new double[1]);
      s.float64s(new double[1]);
      s.bools(new boolean[1]);
      s.int64s(new long[1]);
      s.int64(0);
      s.int64(0);
      s.bool(true);
    });
    try {
      new Accumulator().load(reader(w));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static StateWriter overSamples(int length, int size, int next) {
    return overSamples(length, length, size, next);
  }

  private static StateWriter overSamples(int length, int needsLength, int size, int next) {
    return writer(s -> {
      s.float64s(new double[length]);
      s.int64s(new long[needsLength]);
      s.int32(size);
      s.int32(next);
      s.int32(0);
      s.float64(0);
      s.float64(0);
    });
  }

  @Test
  void anOverSamplesWindowOfTheWrongSizeOrPositionIsRejected() throws Exception {
    Detector d = detector(10);
    new Window.OverSamples(d, 3).load(reader(overSamples(3, 3, 2))); // control
    assertThrows(IOException.class,
        () -> new Window.OverSamples(d, 3).load(reader(overSamples(3, 4, 0, 0))), "needs");
    int[][] bad = {{4, 0, 0}, {2, 0, 0}, {3, 4, 0}, {3, -1, 0}, {3, 0, 3}, {3, 0, -1}};
    for (int[] b : bad) {
      assertThrows(IOException.class,
          () -> new Window.OverSamples(d, 3).load(reader(overSamples(b[0], b[1], b[2]))),
          Arrays.toString(b));
    }
  }

  private static StateWriter overDuration(int entries) {
    return writer(s -> {
      s.int32(entries);
      for (int i = 0; i < entries; i++) {
        s.int64(i);
        s.float64(i);
        s.int64(i);
      }
      s.int64(0);
      s.int32(0);
      s.float64(0);
      s.float64(0);
      s.bool(true);
      s.int64(0);
    });
  }

  @Test
  void anOverDurationWindowBeyondTheRetentionLimitIsRejected() throws Exception {
    Detector d = detector(5);
    new Window.OverDuration(d, 100).load(reader(overDuration(5))); // control: at the limit
    assertThrows(IOException.class,
        () -> new Window.OverDuration(d, 100).load(reader(overDuration(6))));
  }

  private static StateWriter timestamps(int entries) {
    return writer(s -> {
      s.int32(entries);
      for (int i = 0; i < entries; i++) s.int64(10L * i);
      s.int64(7);
    });
  }

  @Test
  void timestampsBeyondTheRetentionLimitAreRejected() throws Exception {
    Detector d = detector(5);
    new Timestamps(d, "R").load(reader(timestamps(5))); // control: at the limit
    assertThrows(IOException.class, () -> new Timestamps(d, "R").load(reader(timestamps(6))));
  }

  @Test
  void moreTimestampsThanTheInitialCapacityRoundTrip() throws Exception {
    Detector d = detector(1_000);
    Timestamps t = new Timestamps(d, "R");
    t.load(reader(timestamps(40)));
    StateWriter out = new StateWriter();
    t.save(out);
    assertArrayEquals(timestamps(40).toByteArray(), out.toByteArray());
  }

  @Test
  void aSnapshotWithTrailingBytesAndAMatchingCrcIsRejected() {
    Detector d = detector(10);
    byte[] ok = d.snapshot();
    byte[] withExtra = Arrays.copyOf(ok, ok.length + 1); // the extra byte sits after the CRC...
    System.arraycopy(ok, ok.length - 4, withExtra, ok.length - 3, 4); // ...so move the CRC after it
    withExtra[ok.length - 4] = 42;
    CRC32 crc = new CRC32();
    crc.update(withExtra, 0, withExtra.length - 4);
    long c = crc.getValue();
    for (int i = 0; i < 4; i++) withExtra[withExtra.length - 4 + i] = (byte) (c >>> (24 - 8 * i));
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().rules(Rule.named("R").when(A.above(1))).retentionLimit(10)
            .restore(withExtra).build());
    assertEquals("Snapshot is corrupted or truncated", e.getMessage());
    // control: the intact one restores
    Detector.builder().rules(Rule.named("R").when(A.above(1))).retentionLimit(10).restore(ok)
        .build();
  }
}
