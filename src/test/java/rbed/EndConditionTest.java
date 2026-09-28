package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class EndConditionTest {
  static final Channel WEIGHT = Channel.named("weight");
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");

  final Recorder rec = new Recorder();

  Detector detector(UnaryOperator<RuleBuilder> rule) {
    return Detector.builder().rule("Lift", rule).listener(rec).build();
  }

  void push(Detector d, long[] ts, double... weights) {
    for (int i = 0; i < ts.length; i++) d.push(ts[i], Map.of(WEIGHT, weights[i]));
  }

  @Test
  void endConditionGivesHysteresisAndEndPhaseResets() {
    Detector d = detector(r -> r
        .when(WEIGHT.above(500))
        .endWhen(WEIGHT.below(50)).forSamples(2));
    push(d, new long[] {0, 1, 2, 3, 4, 5, 6},
        0, 600, 300, 40, 600, 30, 20);
    // 300 no longer satisfies when() but the End Condition does not hold either: still running.
    // End phase at 3 is interrupted by 600 at 4; the second end phase starts at 5.
    assertEquals(List.of("pending Lift 1", "confirmed Lift 1", "ended Lift 1..5"), rec.log);
  }

  @Test
  void endConditionWithTimeDuration() {
    Detector d = detector(r -> r
        .when(WEIGHT.above(500)).forSamples(1)
        .endWhen(WEIGHT.below(50)).forDuration(100));
    push(d, new long[] {0, 10, 20, 60, 130}, 0, 600, 10, 10, 10);
    assertEquals("ended Lift 10..20", rec.log.get(2));
  }

  @Test
  void durationModifierAppliesOnlyToPrecedingCondition() {
    Detector d = detector(r -> r
        .when(WEIGHT.above(500)).forSamples(3)
        .endWhen(WEIGHT.below(50)));
    push(d, new long[] {0, 1, 2, 3, 4}, 0, 600, 600, 600, 10);
    assertEquals(List.of("pending Lift 1", "confirmed Lift 1", "ended Lift 1..4"), rec.log);
  }

  @Test
  void maxGapDiscardsPendingEvent() {
    Detector d = detector(r -> r.when(WEIGHT.above(500)).forSamples(3).maxGap(100));
    push(d, new long[] {0, 10, 20, 500}, 0, 600, 600, 600);
    // the gap discards the first Pending Event; the Sample after the gap starts a new one
    assertEquals(List.of("pending Lift 10", "discarded Lift 10", "pending Lift 500"), rec.log);
  }

  @Test
  void maxGapEndsConfirmedEventTruncatedAtLastSampleBeforeGap() {
    Detector d = detector(r -> r.when(WEIGHT.above(500)).maxGap(100));
    push(d, new long[] {0, 10, 20, 500}, 0, 600, 600, 0);
    assertEquals(List.of("pending Lift 10", "confirmed Lift 10", "ended Lift 10..20 truncatedEnd"),
        rec.log);
  }

  @Test
  void gapWithinMaxGapIsBridgedBySampleAndHold() {
    Detector d = detector(r -> r.when(WEIGHT.above(500)).forDuration(50).maxGap(100));
    push(d, new long[] {0, 10, 90}, 0, 600, 600);
    assertEquals(List.of("pending Lift 10", "confirmed Lift 10"), rec.log);
  }

  @Test
  void newEventNeedsWhenToStopHoldingFirst() {
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.above(10)).endWhen(Y.above(0)))
        .listener(rec).build();
    d.push(0, Map.of(X, 0.0, Y, 0.0));
    d.push(1, Map.of(X, 20.0));
    d.push(2, Map.of(Y, 1.0));   // End Condition holds: Ended
    d.push(3, Map.of(X, 30.0));  // when() still holds, but no new Event without an edge
    d.push(4, Map.of(X, 0.0));   // when() stops holding: re-armed
    d.push(5, Map.of(X, 20.0, Y, 0.0));
    assertEquals(List.of("pending E 1", "confirmed E 1", "ended E 1..2",
        "pending E 5", "confirmed E 5"), rec.log);
  }

  @Test
  void maxGapDuringEndPhaseEndsTruncatedAtLastSampleBeforeGap() {
    Detector d = detector(r -> r
        .when(WEIGHT.above(500))
        .endWhen(WEIGHT.below(50)).forSamples(3)
        .maxGap(100));
    push(d, new long[] {0, 10, 20, 30, 40, 500}, 0, 600, 600, 10, 10, 10);
    assertEquals("ended Lift 10..40 truncatedEnd", rec.log.get(2));
    assertEquals(305, rec.ended.get(0).aggregates(WEIGHT).mean());
  }
}
