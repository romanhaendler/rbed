package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeWeightedAggregatesTest {
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");

  final Recorder rec = new Recorder();

  Aggregates endedX() {
    return rec.ended.get(0).aggregates(X);
  }

  @Test
  void irregularSamplingIsWeightedByTime() {
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.above(0))).listener(rec).build();
    d.push(0, Map.of(X, 0.0));
    d.push(1000, Map.of(X, 10.0));  // holds 3 s
    d.push(4000, Map.of(X, 20.0));  // holds 1 s
    d.push(5000, Map.of(X, 0.0));   // Event ends here
    assertEquals(15, endedX().mean());
    assertEquals(10 * 3 + 20 * 1, endedX().integral());
    assertEquals(50.0 / 4, endedX().timeWeightedMean());
  }

  @Test
  void endPhaseIsExcludedUntilItResets() {
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.above(0)).endWhen(X.below(0)).forSamples(2))
        .listener(rec).build();
    d.push(0, Map.of(X, 0.0));
    d.push(1000, Map.of(X, 10.0));  // 10 for 1 s
    d.push(2000, Map.of(X, -1.0));  // end phase starts, -1 for 1.5 s
    d.push(3500, Map.of(X, 5.0));   // end phase resets; 5 for 0.5 s
    d.push(4000, Map.of(X, -1.0));  // end phase starts again: the Event ends at 4 s
    d.push(5000, Map.of(X, -1.0));
    assertEquals(10 * 1 - 1 * 1.5 + 5 * 0.5, endedX().integral());
    assertEquals(11.0 / 3, endedX().timeWeightedMean());
  }

  @Test
  void truncatedEndExtendsToTheLatestAdvance() {
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.above(0))).listener(rec).build();
    d.push(0, Map.of(X, 0.0));
    d.push(1000, Map.of(X, 4.0));
    d.advanceTo(3000);
    d.close();
    assertEquals(8, endedX().integral());
    assertEquals(4, endedX().timeWeightedMean());
  }

  @Test
  void snapshotsExtendToTheCurrentMoment() {
    List<Event> confirmed = new ArrayList<>();
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.above(0)).forDuration(2000))
        .listener(new EventListener() {
          @Override public void onConfirmed(Event e) { confirmed.add(e); }
        }).build();
    d.push(1000, Map.of(X, 2.0));
    d.push(2000, Map.of(X, 4.0));
    d.push(3000, Map.of(X, 4.0));
    Aggregates x = confirmed.get(0).aggregates(X);
    assertEquals(2 * 1 + 4 * 1, x.integral());
    assertEquals(3, x.timeWeightedMean());
  }

  @Test
  void channelWithoutValueAtTheStartCountsFromItsFirstValue() {
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.above(0)).aggregate(Y)).listener(rec).build();
    d.push(0, Map.of(X, 0.0));
    d.push(1000, Map.of(X, 1.0));
    d.push(2000, Map.of(Y, 6.0));
    d.push(4000, Map.of(X, 0.0));
    Aggregates y = rec.ended.get(0).aggregates(Y);
    assertEquals(12, y.integral());
    assertEquals(6, y.timeWeightedMean());
  }

  @Test
  void zeroSpanHasUndefinedTimeWeightedMean() {
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.above(0))).listener(rec).build();
    d.push(0, Map.of(X, 1.0));
    d.close();
    assertEquals(0, endedX().integral());
    assertTrue(Double.isNaN(endedX().timeWeightedMean()));
  }

  @Test
  void eventStartingOnAnAdvanceIntegratesHeldValuesFromItsStart() {
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.age().above(50))).listener(rec).build();
    d.push(0, Map.of(X, 10.0));
    d.advanceTo(100);             // starts: x has held 10 since 0
    d.push(300, Map.of(X, 10.0)); // fresh value: ends
    assertEquals(10 * 0.2, endedX().integral());
    assertEquals(10, endedX().timeWeightedMean());
  }

  @Test
  void eventWithoutSamplesStillHasTimeWeightedValues() {
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.age().above(50))).listener(rec).build();
    d.push(0, Map.of(X, 10.0));
    d.advanceTo(100);
    d.advanceTo(200);
    d.close();
    assertEquals(0, endedX().count());
    assertTrue(Double.isNaN(endedX().mean()));
    assertEquals(10 * 0.1, endedX().integral());
    assertEquals(10, endedX().timeWeightedMean());
  }
}
