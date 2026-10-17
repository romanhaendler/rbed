package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The Snapshot API: what it restores, and every documented rejection. */
class SnapshotTest {
  static final Channel WEIGHT = Channel.named("weight");
  static final Rule LIFT = Rule.named("Lift").when(WEIGHT.above(500));

  static Detector lift(Recorder rec) {
    return Detector.builder().rules(LIFT).listener(rec).build();
  }

  static void push(Detector d, long t, double w) {
    d.push(t, Map.of(WEIGHT, w));
  }

  /** A Snapshot taken while a Lift is Confirmed. */
  static byte[] runningLift() {
    Detector d = lift(new Recorder());
    push(d, 0, 0);
    push(d, 10, 600);
    return d.snapshot();
  }

  static String message(Detector.Builder b) {
    return assertThrows(IllegalArgumentException.class, b::build).getMessage();
  }

  @Test
  void aRestoredDetectorContinuesAnEventInProgress() {
    Recorder original = new Recorder();
    Detector d = lift(original);
    push(d, 0, 0);
    push(d, 10, 600);
    byte[] snapshot = d.snapshot();

    Recorder resumed = new Recorder();
    Detector twin = Detector.builder().rules(LIFT).listener(resumed).restore(snapshot).build();
    push(twin, 20, 700);
    push(twin, 30, 100);

    assertEquals(List.of("ended Lift 10..30"), resumed.log); // no pending/confirmed again
    assertEquals(600.0, resumed.ended.get(0).aggregates().get(WEIGHT).min());
    assertEquals(700.0, resumed.ended.get(0).aggregates().get(WEIGHT).max());
  }

  @Test
  void aSnapshotBeforeTheFirstSampleRestoresAFreshDetector() {
    byte[] snapshot = lift(new Recorder()).snapshot();
    Recorder rec = new Recorder();
    Detector twin = Detector.builder().rules(LIFT).listener(rec).restore(snapshot).build();
    push(twin, 5, 600);
    assertEquals(List.of("pending Lift 5", "confirmed Lift 5"), rec.log);
  }

  @Test
  void aSnapshotAfterCloseRestoresAClosedDetector() {
    Detector d = lift(new Recorder());
    push(d, 0, 600);
    d.close();
    Detector twin = Detector.builder().rules(LIFT).restore(d.snapshot()).build();
    assertThrows(IllegalStateException.class, () -> push(twin, 10, 0));
  }

  @Test
  void aSnapshotOfARestoredDetectorIsIdentical() {
    byte[] snapshot = runningLift();
    Detector twin = Detector.builder().rules(LIFT).restore(snapshot).build();
    assertArrayEquals(snapshot, twin.snapshot());
  }

  @Test
  void takingASnapshotDoesNotChangeTheDetector() {
    Recorder a = new Recorder();
    Recorder b = new Recorder();
    Detector plain = lift(a);
    Detector snapped = lift(b);
    double[] weights = {0, 600, 700, 100, 800, 0};
    for (int i = 0; i < weights.length; i++) {
      push(plain, i * 10, weights[i]);
      snapped.snapshot();
      push(snapped, i * 10, weights[i]);
      snapped.snapshot();
    }
    assertEquals(a.log, b.log);
  }

  @Test
  void aSnapshotInsideACallbackIsRejected() {
    List<String> messages = new ArrayList<>();
    Detector[] holder = new Detector[1];
    holder[0] = Detector.builder().rules(LIFT).listener(new EventListener() {
      @Override public void onConfirmed(Event e) {
        try {
          holder[0].snapshot();
        } catch (IllegalStateException ex) {
          messages.add(ex.getMessage());
        }
      }

      @Override public void onEnded(Event e) {
        assertThrows(IllegalStateException.class, () -> holder[0].snapshot());
        messages.add("ended");
      }
    }).build();
    push(holder[0], 0, 600);
    push(holder[0], 10, 0);
    assertEquals(2, messages.size());
    assertTrue(messages.get(0).contains("listener callback"), messages.get(0));
    holder[0].snapshot(); // fine again between pushes
    holder[0].close();
    holder[0].snapshot();
  }

  @Test
  void aSnapshotInsideACallbackDuringCloseOrAdvanceIsRejected() {
    int[] rejected = {0};
    Detector[] holder = new Detector[1];
    holder[0] = Detector.builder().rules(LIFT).listener(new EventListener() {
      @Override public void onEnded(Event e) {
        assertThrows(IllegalStateException.class, () -> holder[0].snapshot());
        rejected[0]++;
      }
    }).build();
    push(holder[0], 0, 600);
    holder[0].close();
    assertEquals(1, rejected[0]);
  }

  @Test
  void aDifferentRuleIsRejected() {
    Rule other = Rule.named("Lift").when(WEIGHT.above(501));
    String m = message(Detector.builder().rules(other).restore(runningLift()));
    assertTrue(m.contains("different Rules"), m);
  }

  @Test
  void aRenamedRuleIsRejected() {
    Rule renamed = Rule.named("Raise").when(WEIGHT.above(500));
    String m = message(Detector.builder().rules(renamed).restore(runningLift()));
    assertTrue(m.contains("different Rules"), m);
  }

  @Test
  void anAddedRuleIsRejected() {
    Rule more = Rule.named("Heavy").when(WEIGHT.above(900));
    String m = message(Detector.builder().rules(LIFT, more).restore(runningLift()));
    assertTrue(m.contains("different Rules"), m);
  }

  @Test
  void aChannelOfAnotherKindIsRejected() {
    Rule asPulse = Rule.named("Lift").when(Channel.pulse("weight").above(500));
    String m = message(Detector.builder().rules(asPulse).restore(runningLift()));
    assertTrue(m.contains("different Rules"), m);
  }

  @Test
  void aDifferentSamplingRateIsRejected() {
    Detector d = Detector.builder().rules(LIFT).samplingRate(10).build();
    byte[] snapshot = d.snapshot();
    String m = message(Detector.builder().rules(LIFT).samplingRate(20).restore(snapshot));
    assertTrue(m.contains("sampling rate"), m);
    m = message(Detector.builder().rules(LIFT).restore(snapshot));
    assertTrue(m.contains("sampling rate"), m);
  }

  @Test
  void aDifferentRetentionLimitIsRejected() {
    String m = message(Detector.builder().rules(LIFT).retentionLimit(5).restore(runningLift()));
    assertTrue(m.contains("Retention Limit"), m);
  }

  @Test
  void differentWatchedExpressionsAreRejected() {
    String m = message(Detector.builder().rules(LIFT).watch(WEIGHT).restore(runningLift()));
    assertTrue(m.contains("watched Expressions"), m);
    byte[] withWatch = Detector.builder().rules(LIFT).watch(WEIGHT).build().snapshot();
    m = message(Detector.builder().rules(LIFT).watch(WEIGHT.rate()).restore(withWatch));
    assertTrue(m.contains("watched Expressions"), m);
  }

  @Test
  void anUnsupportedFormatVersionIsRejected() {
    byte[] snapshot = runningLift();
    snapshot[7] = 99; // the format version is the int after the 4-byte magic header
    String m = message(Detector.builder().rules(LIFT).restore(snapshot));
    assertTrue(m.contains("format version 99") && m.contains("Replay Point"), m);
  }

  @Test
  void somethingThatIsNotASnapshotIsRejected() {
    String m = message(Detector.builder().rules(LIFT).restore(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}));
    assertTrue(m.contains("Not an rbed Snapshot"), m);
    m = message(Detector.builder().rules(LIFT).restore(new byte[0]).restore("RBED".getBytes()));
    assertTrue(m.contains("corrupted or truncated"), m);
  }

  @Test
  void everyTruncationIsRejected() {
    byte[] snapshot = runningLift();
    for (int n = 0; n < snapshot.length; n++) {
      byte[] cut = java.util.Arrays.copyOf(snapshot, n);
      assertThrows(IllegalArgumentException.class,
          () -> Detector.builder().rules(LIFT).restore(cut).build(), "length " + n);
    }
  }

  @Test
  void everyFlippedByteIsRejected() {
    byte[] snapshot = runningLift();
    for (int i = 0; i < snapshot.length; i++) {
      byte[] bad = snapshot.clone();
      bad[i] ^= 0x01;
      int at = i;
      assertThrows(IllegalArgumentException.class,
          () -> Detector.builder().rules(LIFT).restore(bad).build(), "byte " + at);
    }
  }

  @Test
  void restoreTogetherWithHistoryIsRejected() {
    Event old = new Event("Lift", 0, java.util.OptionalLong.of(0), java.util.OptionalLong.of(5),
        false, false, Map.of());
    String m = message(Detector.builder().rules(LIFT).history(List.of(old)).restore(runningLift()));
    assertTrue(m.contains("restore") && m.contains("history"), m);
    m = message(Detector.builder().rules(LIFT).restore(runningLift()).history(List.of(old)));
    assertTrue(m.contains("restore") && m.contains("history"), m);
  }

  @Test
  void restoreRejectsNull() {
    assertThrows(NullPointerException.class, () -> Detector.builder().restore(null));
  }
}
