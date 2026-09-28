package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WalkingSkeletonTest {
  static final Channel WEIGHT = Channel.named("weight");

  final Recorder rec = new Recorder();

  Detector liftDetector() {
    return Detector.builder()
        .rules(Rule.named("Lift").when(WEIGHT.above(500)))
        .listener(rec)
        .build();
  }

  @Test
  void thresholdCrossingIsPendingConfirmedAndEnded() {
    Detector d = liftDetector();
    d.push(0, Map.of(WEIGHT, 0.0));
    d.push(10, Map.of(WEIGHT, 600.0));
    d.push(20, Map.of(WEIGHT, 700.0));
    d.push(30, Map.of(WEIGHT, 100.0));
    assertEquals(List.of("pending Lift 10", "confirmed Lift 10", "ended Lift 10..30"), rec.log);
  }

  @Test
  void closeEndsRunningEventAsTruncated() {
    Detector d = liftDetector();
    d.push(0, Map.of(WEIGHT, 0.0));
    d.push(10, Map.of(WEIGHT, 600.0));
    d.push(20, Map.of(WEIGHT, 600.0));
    d.close();
    assertEquals("ended Lift 10..20 truncatedEnd", rec.log.get(2));
    assertThrows(IllegalStateException.class, () -> d.push(30, Map.of(WEIGHT, 0.0)));
  }

  @Test
  void rejectsNonIncreasingTimestamps() {
    Detector d = liftDetector();
    d.push(10, Map.of(WEIGHT, 0.0));
    assertThrows(IllegalArgumentException.class, () -> d.push(10, Map.of(WEIGHT, 0.0)));
    assertThrows(IllegalArgumentException.class, () -> d.push(5, Map.of(WEIGHT, 0.0)));
  }

  @Test
  void rejectsNonFiniteValues() {
    Detector d = liftDetector();
    assertThrows(IllegalArgumentException.class, () -> d.push(0, Map.of(WEIGHT, Double.NaN)));
    assertThrows(IllegalArgumentException.class,
        () -> d.push(0, Map.of(WEIGHT, Double.POSITIVE_INFINITY)));
  }
}
