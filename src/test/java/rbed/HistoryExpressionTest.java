package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoryExpressionTest {
  final Recorder rec = new Recorder();

  List<String> run(Detector.Builder b, long[] ts, double[] xs) {
    Detector d = b.listener(rec).build();
    for (int i = 0; i < ts.length; i++) d.push(ts[i], Map.of("x", xs[i]));
    d.close();
    return rec.endedLines();
  }

  // Events that start right after a warm-up are truncated: the Rule never saw them not holding.

  @Test
  void risingAndFallingCompareWithPreviousValue() {
    assertEquals(List.of("ended Up 1..2 truncatedStart", "ended Down 3..3 truncatedEnd"), run(Detector.builder()
            .rule("Up", r -> r.when(channel("x").rising()))
            .rule("Down", r -> r.when(channel("x").falling())),
        new long[] {0, 1, 2, 3}, new double[] {1, 2, 2, 1}));
  }

  @Test
  void rateIsChangePerSecondBetweenTheChannelsLastTwoValues() {
    Detector d = Detector.builder()
        .rule("Fast", r -> r.when(channel("x").rate().above(15)))
        .listener(rec).build();
    d.push(0, Map.of("x", 0.0));
    d.push(500, Map.of("x", 10.0));   // 20 per second
    d.push(700, Map.of("y", 1.0));    // x not in Sample: rate still from its last two values
    d.push(1500, Map.of("x", 11.0));  // 1 per second
    assertEquals(List.of("pending Fast 500", "confirmed Fast 500", "ended Fast 500..1500 truncatedStart"),
        rec.log);
  }

  @Test
  void movingAverageOverSamplesIsUndefinedDuringWarmUp() {
    assertEquals(List.of("ended E 2..5 truncatedStart"), run(Detector.builder()
            .rule("E", r -> r.when(channel("x").averageOverSamples(3).above(5))),
        new long[] {0, 1, 2, 3, 4, 5}, new double[] {9, 9, 9, 9, 0, 0}));
  }

  @Test
  void movingAverageOverTimeKeepsOnlyItsWindow() {
    assertEquals(List.of("ended Defined 20..30 truncatedStart truncatedEnd", "ended High 30..30 truncatedEnd"),
        run(Detector.builder()
            .rule("Defined", r -> r.when(channel("x").averageOverDuration(Duration.ofMillis(20)).above(-1)))
            .rule("High", r -> r.when(channel("x").averageOverDuration(20).above(15))),
        new long[] {0, 10, 20, 30}, new double[] {0, 10, 20, 30}));
  }

  @Test
  void movingAverageInEndConditionCollectsFromTheFirstSample() {
    assertEquals(List.of("ended E 2..5"), run(Detector.builder()
            .rule("E", r -> r
                .when(channel("x").above(100)).forSamples(3)
                .endWhen(channel("x").averageOverSamples(2).below(150))),
        new long[] {0, 1, 2, 3, 4, 5}, new double[] {5, 5, 200, 200, 200, 5}));
  }

  /** A huge value leaving the window must not wipe out the small values that remain. */
  @Test
  void movingAveragesDoNotLosePrecisionWhenLargeValuesLeave() {
    assertEquals(List.of("ended Samples 2..2 truncatedEnd",
            "ended Time 2..2 truncatedEnd"), run(Detector.builder()
            .rule("Samples", r -> r.when(channel("x").averageOverSamples(2).between(0.99, 1.01)))
            .rule("Time", r -> r.when(channel("x").averageOverDuration(1).between(0.99, 1.01))),
        new long[] {0, 1, 2}, new double[] {1e16, 1, 1}));
  }
}
