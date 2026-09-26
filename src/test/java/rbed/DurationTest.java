package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DurationTest {
  final Recorder rec = new Recorder();

  Detector detector(java.util.function.UnaryOperator<RuleBuilder> rule) {
    return Detector.builder().rule("E", rule).listener(rec).build();
  }

  @Test
  void forSamplesConfirmsOnTheNthConsecutiveSample() {
    Detector d = detector(r -> r.when(channel("x").above(0)).forSamples(3));
    long[] ts = {0, 10, 20, 30, 40};
    double[] xs = {0, 1, 1, 1, 0};
    for (int i = 0; i < ts.length; i++) d.push(ts[i], Map.of("x", xs[i]));
    assertEquals(List.of("pending E 10", "confirmed E 10", "ended E 10..40"), rec.log);
  }

  @Test
  void forDurationConfirmsOnFirstSampleReachingTheDuration() {
    Detector d = detector(r -> r.when(channel("x").above(0)).forDuration(Duration.ofMillis(5)));
    d.push(0, Map.of("x", 0.0));
    d.push(1, Map.of("x", 1.0));
    d.push(4, Map.of("x", 1.0));
    assertEquals(List.of("pending E 1"), rec.log);
    d.push(9, Map.of("x", 1.0)); // 9 - 1 >= 5, even though no Sample fell on 6
    assertEquals(List.of("pending E 1", "confirmed E 1"), rec.log);
  }

  @Test
  void shortSpikeIsDiscarded() {
    Detector d = detector(r -> r.when(channel("x").above(0)).forSamples(3));
    d.push(0, Map.of("x", 1.0));
    d.push(1, Map.of("x", 1.0));
    d.push(2, Map.of("x", 0.0));
    assertEquals(List.of("pending E 0", "discarded E 0"), rec.log);
  }

  @Test
  void eventInProgressAtFirstSampleHasTruncatedStart() {
    Detector d = detector(r -> r.when(channel("x").above(0)).forSamples(2));
    d.push(0, Map.of("x", 1.0));
    d.push(1, Map.of("x", 1.0));
    d.push(2, Map.of("x", 0.0));
    assertEquals("ended E 0..2 truncatedStart", rec.log.get(2));
  }

  @Test
  void closeDiscardsPendingEvent() {
    Detector d = detector(r -> r.when(channel("x").above(0)).forDuration(100));
    d.push(0, Map.of("x", 0.0));
    d.push(1, Map.of("x", 1.0));
    d.close();
    assertEquals(List.of("pending E 1", "discarded E 1"), rec.log);
  }
}
