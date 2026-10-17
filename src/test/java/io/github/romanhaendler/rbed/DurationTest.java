package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DurationTest {
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");

  final Recorder rec = new Recorder();

  Detector detector(java.util.function.UnaryOperator<Rule> rule) {
    return Detector.builder().rules(rule.apply(Rule.named("E"))).listener(rec).build();
  }

  @Test
  void forSamplesConfirmsOnTheNthConsecutiveSample() {
    Detector d = detector(r -> r.when(X.above(0)).forSamples(3));
    long[] ts = {0, 10, 20, 30, 40};
    double[] xs = {0, 1, 1, 1, 0};
    for (int i = 0; i < ts.length; i++) d.push(ts[i], Map.of(X, xs[i]));
    assertEquals(List.of("pending E 10", "confirmed E 10", "ended E 10..40"), rec.log);
  }

  @Test
  void forDurationConfirmsOnFirstSampleReachingTheDuration() {
    Detector d = detector(r -> r.when(X.above(0)).forDuration(Duration.ofMillis(5)));
    d.push(0, Map.of(X, 0.0));
    d.push(1, Map.of(X, 1.0));
    d.push(4, Map.of(X, 1.0));
    assertEquals(List.of("pending E 1"), rec.log);
    d.push(9, Map.of(X, 1.0)); // 9 - 1 >= 5, even though no Sample fell on 6
    assertEquals(List.of("pending E 1", "confirmed E 1"), rec.log);
  }

  @Test
  void shortSpikeIsDiscarded() {
    Detector d = detector(r -> r.when(X.above(0)).forSamples(3));
    d.push(0, Map.of(X, 1.0));
    d.push(1, Map.of(X, 1.0));
    d.push(2, Map.of(X, 0.0));
    assertEquals(List.of("pending E 0", "discarded E 0"), rec.log);
  }

  @Test
  void eventInProgressAtFirstSampleHasTruncatedStart() {
    Detector d = detector(r -> r.when(X.above(0)).forSamples(2));
    d.push(0, Map.of(X, 1.0));
    d.push(1, Map.of(X, 1.0));
    d.push(2, Map.of(X, 0.0));
    assertEquals("ended E 0..2 truncatedStart", rec.log.get(2));
  }

  @Test
  void readmeTruncationExample() {
    // "The Event lifecycle", Truncation: exactly as printed in the README.
    Channel temp = Channel.named("temp");
    Rule overheat = Rule.named("Overheat").when(temp.above(80)).forDuration(2_000);
    Detector detector = Detector.builder().rules(overheat).listener(rec).build();
    detector.push(0,     Map.of(temp, 90.0));
    detector.push(1_000, Map.of(temp, 92.0));
    detector.push(2_000, Map.of(temp, 91.0));
    detector.push(3_000, Map.of(temp, 70.0));
    assertEquals(List.of("pending Overheat 0", "confirmed Overheat 0",
        "ended Overheat 0..3000 truncatedStart"), rec.log);
    assertEquals(2_000, rec.ended.get(0).confirmed().getAsLong());
  }

  @Test
  void closeDiscardsPendingEvent() {
    Detector d = detector(r -> r.when(X.above(0)).forDuration(100));
    d.push(0, Map.of(X, 0.0));
    d.push(1, Map.of(X, 1.0));
    d.close();
    assertEquals(List.of("pending E 1", "discarded E 1"), rec.log);
  }

  @Test
  void startIsTruncatedUntilTheConditionWasSeenFalse() {
    Detector d = detector(r -> r.when(X.above(0)));
    d.push(0, Map.of(Y, 1.0)); // x unknown: the Event may already be in progress
    d.push(1, Map.of(X, 5.0));
    d.close();
    assertEquals("ended E 1..1 truncatedStart truncatedEnd", rec.log.get(2));
  }

  @Test
  void startAfterAGapIsTruncated() {
    Detector d = detector(r -> r.when(X.above(0)).maxGap(10));
    d.push(0, Map.of(X, 0.0));
    d.push(100, Map.of(X, 5.0)); // became true at some point during the gap
    d.push(101, Map.of(X, 0.0));
    assertEquals("ended E 100..101 truncatedStart", rec.log.get(2));
  }
}
