package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AggregatesTest {
  static final Channel HEIGHT = Channel.named("height");
  static final Channel NOPE = Channel.named("nope");
  static final Channel WEIGHT = Channel.named("weight");

  final Recorder rec = new Recorder();

  @Test
  void aggregatesCoverEventSamplesExcludingTheEndSample() {
    Detector d = Detector.builder()
        .rules(Rule.named("Lift").when(WEIGHT.above(500)).aggregate(HEIGHT))
        .listener(rec).build();
    d.push(0, Map.of(WEIGHT, 0.0, HEIGHT, 1.0));
    d.push(1, Map.of(WEIGHT, 600.0));
    d.push(2, Map.of(WEIGHT, 700.0, HEIGHT, 5.0));
    d.push(3, Map.of(WEIGHT, 650.0));
    d.push(4, Map.of(WEIGHT, 100.0, HEIGHT, 9.0));
    Event e = rec.ended.get(0);
    assertEquals(Recorder.stats(600, 700, 650, 3), Recorder.stats(e.aggregates(WEIGHT)));
    // height is held at 1 for the Sample at 1, then 5 for the Samples at 2 and 3
    assertEquals(Recorder.stats(1, 5, 11.0 / 3, 3), Recorder.stats(e.aggregates(HEIGHT)));
  }

  @Test
  void requestedChannelWithoutValuesHasEmptyAggregates() {
    Detector d = Detector.builder()
        .rules(Rule.named("Lift").when(WEIGHT.above(500)).aggregate(NOPE))
        .listener(rec).build();
    d.push(0, Map.of(WEIGHT, 600.0));
    d.close();
    Aggregates a = rec.ended.get(0).aggregates(NOPE);
    assertEquals(0, a.count());
    assertTrue(Double.isNaN(a.mean()));
  }

  @Test
  void interruptedEndPhaseSamplesCountAndFinalEndPhaseSamplesDoNot() {
    Detector d = Detector.builder()
        .rules(Rule.named("Lift")
            .when(WEIGHT.above(500))
            .endWhen(WEIGHT.below(50)).forSamples(2))
        .listener(rec).build();
    double[] ws = {0, 600, 300, 40, 600, 30, 20};
    for (int i = 0; i < ws.length; i++) d.push(i, Map.of(WEIGHT, ws[i]));
    assertEquals(Recorder.stats(40, 600, 385, 4), Recorder.stats(rec.ended.get(0).aggregates(WEIGHT)));
  }

  @Test
  void truncatedEndIncludesLastSample() {
    Detector d = Detector.builder()
        .rules(Rule.named("Lift").when(WEIGHT.above(500)))
        .listener(rec).build();
    d.push(0, Map.of(WEIGHT, 600.0));
    d.push(1, Map.of(WEIGHT, 800.0));
    d.close();
    assertEquals(Recorder.stats(600, 800, 700, 2), Recorder.stats(rec.ended.get(0).aggregates(WEIGHT)));
  }

  @Test
  void confirmedSnapshotHasAggregatesSoFar() {
    List<Event> confirmed = new ArrayList<>();
    Detector d = Detector.builder()
        .rules(Rule.named("Lift").when(WEIGHT.above(500)).forSamples(2))
        .listener(new EventListener() {
          @Override public void onConfirmed(Event e) { confirmed.add(e); }
        }).build();
    d.push(0, Map.of(WEIGHT, 0.0));
    d.push(1, Map.of(WEIGHT, 600.0));
    d.push(2, Map.of(WEIGHT, 800.0));
    assertEquals(Recorder.stats(600, 800, 700, 2), Recorder.stats(confirmed.get(0).aggregates(WEIGHT)));
  }
}
