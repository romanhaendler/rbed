package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeAdvanceTest {
  static final Channel HR = Channel.named("hr");
  static final Channel X = Channel.named("x");

  final Recorder rec = new Recorder();

  Detector detector(java.util.function.UnaryOperator<RuleBuilder> rule) {
    return Detector.builder().rule("E", rule).listener(rec).build();
  }

  @Test
  void silentSensorRaisesAnEventWhileNoSamplesArrive() {
    Detector d = Detector.builder()
        .rule("SensorLost", r -> r.when(HR.age().above(10_000)))
        .listener(rec).build();
    d.push(0, Map.of(HR, 70.0));
    d.advanceTo(5_000);
    d.advanceTo(10_001);
    assertEquals(List.of("pending SensorLost 10001", "confirmed SensorLost 10001"), rec.log);
    d.push(12_000, Map.of(HR, 72.0));
    assertEquals("ended SensorLost 10001..12000", rec.log.get(2));
  }

  @Test
  void ageIsUndefinedBeforeTheFirstValue() {
    Detector d = detector(r -> r.when(HR.age().above(-1)));
    d.advanceTo(100);
    d.push(200, Map.of(X, 1.0));
    assertEquals(List.of(), rec.log);
  }

  @Test
  void durationIsReachedOnAnAdvance() {
    Detector d = detector(r -> r.when(X.above(0)).forDuration(100));
    d.push(0, Map.of(X, 0.0));
    d.push(10, Map.of(X, 1.0));
    d.advanceTo(110);
    assertEquals(List.of("pending E 10", "confirmed E 10"), rec.log);
  }

  @Test
  void endConditionDurationIsReachedOnAnAdvance() {
    Detector d = detector(r -> r
        .when(X.above(0))
        .endWhen(X.below(0)).forDuration(100));
    d.push(0, Map.of(X, 0.0));
    d.push(10, Map.of(X, 1.0));
    d.push(20, Map.of(X, -1.0));
    d.advanceTo(120);
    assertEquals("ended E 10..20", rec.log.get(2));
  }

  @Test
  void maxGapIsNoticedOnAnAdvance() {
    Detector d = detector(r -> r.when(X.above(0)).maxGap(100));
    d.push(0, Map.of(X, 0.0));
    d.push(10, Map.of(X, 1.0));
    d.advanceTo(50);   // still within maxGap of the last Sample: valid
    d.advanceTo(111);  // stale: truncated at the last valid moment, 50
    d.advanceTo(200);
    assertEquals(List.of("pending E 10", "confirmed E 10", "ended E 10..50 truncatedEnd"), rec.log);
  }

  @Test
  void advancesAreNotSamples() {
    Detector d = detector(r -> r.when(X.above(0)).forSamples(2));
    d.push(0, Map.of(X, 0.0));
    d.push(10, Map.of(X, 5.0));
    d.advanceTo(20);
    d.advanceTo(30);
    assertEquals(List.of("pending E 10"), rec.log);
    d.push(40, Map.of(X, 7.0));
    d.push(50, Map.of(X, 0.0));
    assertEquals(Recorder.stats(5, 7, 6, 2), Recorder.stats(rec.ended.get(0).aggregates(X)));
  }

  @Test
  void closeAfterAnAdvanceEndsAtTheAdvance() {
    Detector d = detector(r -> r.when(X.above(0)));
    d.push(0, Map.of(X, 0.0));
    d.push(10, Map.of(X, 1.0));
    d.advanceTo(70);
    d.close();
    assertEquals("ended E 10..70 truncatedEnd", rec.log.get(2));
  }

  @Test
  void misuseIsRejected() {
    Detector d = detector(r -> r.when(X.above(0)));
    d.push(10, Map.of(X, 0.0));
    assertThrows(IllegalArgumentException.class, () -> d.advanceTo(10));
    d.close();
    assertThrows(IllegalStateException.class, () -> d.advanceTo(20));
    Detector sampled = Detector.builder().samplingRate(1)
        .rule("E", r -> r.when(X.above(0))).build();
    assertThrows(IllegalStateException.class, () -> sampled.advanceTo(20));
  }

  @Test
  void maxGapAfterAdvancesTruncatesAtTheLastValidMoment() {
    Detector d = detector(r -> r.when(X.above(0)).forDuration(500).maxGap(1000));
    d.push(0, Map.of(X, 1.0));
    d.advanceTo(600);   // Confirmed on the held value
    d.advanceTo(1200);  // data stale: the Event cannot end before it was Confirmed
    assertEquals(List.of("pending E 0", "confirmed E 0",
        "ended E 0..600 truncatedStart truncatedEnd"), rec.log);
  }

  @Test
  void eventStartedOnAnAdvanceIsTruncatedNoEarlierThanItsStart() {
    Detector d = detector(r -> r.when(X.age().above(100)).maxGap(1000));
    d.push(0, Map.of(X, 1.0));
    d.advanceTo(200);
    d.advanceTo(1100);
    assertEquals(List.of("pending E 200", "confirmed E 200", "ended E 200..200 truncatedEnd"),
        rec.log);
  }
}
