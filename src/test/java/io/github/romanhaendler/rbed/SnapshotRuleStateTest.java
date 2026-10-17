package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Differential.Op;
import io.github.romanhaendler.rbed.Differential.Result;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Twin runs for each piece of a Rule's state: a Snapshot at every moment of a hand-made input
 * restores a Detector that never differs from the original. Each input puts a Snapshot into the
 * state the piece is about, and the test checks that the interesting Events happened.
 */
class SnapshotRuleStateTest {
  private static final Channel A = Channel.named("a");
  private static final Channel B = Channel.named("b");
  private static final Channel P = Channel.pulse("p");

  private static Map<String, Integer> twins(Consumer<Detector.Builder> config, List<Op> ops) {
    Map<String, Integer> stats = new TreeMap<>();
    for (int k = 0; k <= ops.size(); k++) {
      Result r = Differential.compareRestoredAt(config, config, List.of(), ops, k);
      if (r.difference() != null) {
        throw new AssertionError("Snapshot after " + k + " ops\n" + r.difference().report());
      }
      r.stats().forEach((name, n) -> stats.merge(name, n, Integer::sum));
    }
    return stats;
  }

  private static void seen(Map<String, Integer> stats, String... what) {
    for (String s : what) assertTrue(stats.getOrDefault(s, 0) > 0, s + " never happened: " + stats);
  }

  @Test
  void forSamplesCountsTheSamplesBeforeAndAfterTheSnapshot() {
    Rule r = Rule.named("R").when(A.above(1)).forSamples(3);
    Map<String, Integer> s = twins(b -> b.rules(r), List.of(
        Op.push(0, "a", 0), Op.push(1, "a", 2), Op.push(2, "a", 2), Op.push(3, "a", 2),
        Op.push(4, "a", 0), Op.push(5, "a", 2), Op.push(6, "a", 2), Op.push(7, "a", 0)));
    seen(s, "pending", "confirmed", "ended", "discarded");
  }

  @Test
  void forDurationMeasuresTimeAcrossTheSnapshot() {
    Rule r = Rule.named("R").when(A.above(1)).forDuration(25);
    Map<String, Integer> s = twins(b -> b.rules(r), List.of(
        Op.push(0, "a", 0), Op.push(10, "a", 2), Op.push(20, "a", 2), Op.push(34, "a", 2),
        Op.push(40, "a", 2), Op.push(50, "a", 0), Op.push(60, "a", 2), Op.push(70, "a", 0)));
    seen(s, "pending", "confirmed", "ended", "discarded");
  }

  @Test
  void endConditionsWithAndWithoutADurationRunAcrossTheSnapshot() {
    Rule plain = Rule.named("Plain").when(A.above(1)).endWhen(B.above(1));
    Rule timed = Rule.named("Timed").when(A.above(1)).endWhen(B.above(1)).forDuration(5);
    Rule counted = Rule.named("Counted").when(A.above(1)).endWhen(B.above(1)).forSamples(2);
    Map<String, Integer> s = twins(b -> b.rules(plain, timed, counted), List.of(
        Op.push(0, "a", 0, "b", 0), Op.push(10, "a", 2, "b", 0), Op.push(20, "a", 2, "b", 2),
        Op.push(30, "a", 2, "b", 0), Op.push(40, "a", 2, "b", 2), Op.push(50, "a", 2, "b", 2),
        Op.push(55, "a", 2, "b", 2), Op.push(60, "a", 0, "b", 0), Op.push(70, "a", 2, "b", 0),
        Op.push(80, "a", 2, "b", 2), Op.push(90, "a", 0, "b", 0)));
    seen(s, "pending", "confirmed", "ended");
  }

  /** The restart rule: after an End Condition ended it, a Rule whose when still holds waits. */
  @Test
  void aRuleWhoseWhenStillHoldsAfterItsEndConditionDoesNotRestartUntilWhenFalls() {
    Rule r = Rule.named("R").when(A.above(1)).endWhen(B.above(1));
    Map<String, Integer> s = twins(b -> b.rules(r), List.of(
        Op.push(0, "a", 2, "b", 0), Op.push(10, "a", 2, "b", 2), Op.push(20, "a", 2, "b", 0),
        Op.push(30, "a", 2, "b", 0), Op.push(40, "a", 0, "b", 0), Op.push(50, "a", 2, "b", 0),
        Op.push(60, "a", 2, "b", 2), Op.push(70, "a", 0, "b", 0)));
    seen(s, "confirmed", "ended", "truncatedStart");
  }

  /** Whether when was seen not holding decides truncatedStart; so does a gap that resets it. */
  @Test
  void theKnowledgeThatWhenWasSeenFalseSurvivesTheSnapshot() {
    Rule r = Rule.named("R").when(A.above(1)).maxGap(50);
    Map<String, Integer> s = twins(b -> b.rules(r), List.of(
        Op.push(0, "a", 2), Op.push(10, "a", 0), Op.push(20, "a", 2), Op.push(30, "a", 0),
        Op.push(100, "a", 2), Op.push(110, "a", 2), Op.push(120, "a", 0)));
    seen(s, "truncatedStart", "ended");
  }

  @Test
  void maxGapWithGapsAroundTheLimit() {
    Rule r = Rule.named("R").when(A.above(1)).maxGap(50);
    Rule timed = Rule.named("T").when(A.above(1)).forDuration(100).maxGap(50);
    Map<String, Integer> s = twins(b -> b.rules(r, timed), List.of(
        Op.push(0, "a", 2), Op.push(49, "a", 2), Op.push(99, "a", 2), Op.push(150, "a", 2),
        Op.push(200, "a", 0), Op.push(249, "a", 2), Op.push(300, "a", 2), Op.push(352, "a", 2),
        Op.push(353, "a", 0), Op.push(403, "a", 2), Op.push(500, "a", 0)));
    seen(s, "ended", "truncatedEnd", "truncatedStart");
  }

  @Test
  void timeAdvancesBeforeAndAfterTheSnapshot() {
    Rule r = Rule.named("R").when(A.above(1)).forDuration(30).maxGap(40);
    Rule e = Rule.named("E").when(A.above(1)).endWhen(B.above(1)).forDuration(20);
    Map<String, Integer> s = twins(b -> b.rules(r, e), List.of(
        Op.push(0, "a", 2, "b", 0), Op.advance(10), Op.push(15, "a", 2, "b", 0), Op.advance(30),
        Op.push(35, "a", 2, "b", 2), Op.advance(50), Op.advance(60), Op.push(70, "a", 0, "b", 2),
        Op.advance(200), Op.push(210, "a", 2, "b", 0), Op.advance(300), Op.close()));
    seen(s, "confirmed", "ended");
  }

  @Test
  void pulseChannelsPresentAndAbsent() {
    Rule r = Rule.named("R").when(P.above(0.5)).forSamples(2);
    Rule n = Rule.named("N").when(P.above(0.5).not()).forDuration(15);
    Map<String, Integer> s = twins(b -> b.rules(r, n), List.of(
        Op.push(0, "a", 0), Op.push(10, "a", 0, "p", 1), Op.push(20, "a", 0, "p", 1),
        Op.push(30, "a", 0), Op.push(40, "a", 0), Op.push(50, "a", 0, "p", 1),
        Op.push(60, "a", 0), Op.push(70, "a", 0, "p", 1)));
    seen(s, "confirmed", "ended");
  }

  @Test
  void closeEndsEventsAndALaterSnapshotRestoresTheClosedDetector() {
    Rule r = Rule.named("R").when(A.above(1)).forSamples(2);
    Rule e = Rule.named("E").when(A.above(1)).endWhen(B.above(1));
    Map<String, Integer> s = twins(b -> b.rules(r, e), List.of(
        Op.push(0, "a", 2, "b", 0), Op.push(10, "a", 2, "b", 0), Op.close(), Op.close(),
        Op.push(20, "a", 2, "b", 0), Op.advance(30)));
    seen(s, "confirmed", "truncatedEnd", "exception");
  }

  /** The restored Detector derives the same timestamps from its Sample index. */
  @Test
  void samplingRateModeContinuesTheSampleIndex() {
    Rule r = Rule.named("R").when(A.above(1)).forDuration(1500);
    Map<String, Integer> s = twins(b -> b.samplingRate(2).rules(r), List.of(
        Op.pushValues("a", 0), Op.pushValues("a", 2), Op.pushValues("a", 2), Op.pushValues("a", 2),
        Op.pushValues("a", 2), Op.pushValues("a", 0), Op.pushValues("a", 2), Op.advance(9000)));
    seen(s, "confirmed", "ended");
  }
}
