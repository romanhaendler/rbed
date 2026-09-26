package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rbed.Conditions.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AggregatesTest {
  final Recorder rec = new Recorder();

  @Test
  void aggregatesCoverEventSamplesExcludingTheEndSample() {
    Detector d = Detector.builder()
        .rule("Lift", r -> r.when(channel("weight").above(500)))
        .listener(rec).build();
    d.push(0, Map.of("weight", 0.0, "height", 1.0));
    d.push(1, Map.of("weight", 600.0));
    d.push(2, Map.of("weight", 700.0, "height", 5.0));
    d.push(3, Map.of("weight", 650.0));
    d.push(4, Map.of("weight", 100.0, "height", 9.0));
    Event e = rec.ended.get(0);
    assertEquals(new Aggregates(600, 700, 650, 3), e.aggregates("weight"));
    // height is held at 1 for the Sample at 1, then 5 for the Samples at 2 and 3
    assertEquals(new Aggregates(1, 5, 11.0 / 3, 3), e.aggregates("height"));
  }

  @Test
  void unknownChannelHasEmptyAggregates() {
    Detector d = Detector.builder()
        .rule("Lift", r -> r.when(channel("weight").above(500)))
        .listener(rec).build();
    d.push(0, Map.of("weight", 600.0));
    d.close();
    Aggregates a = rec.ended.get(0).aggregates("nope");
    assertEquals(0, a.count());
    assertTrue(Double.isNaN(a.mean()));
  }

  @Test
  void interruptedEndPhaseSamplesCountAndFinalEndPhaseSamplesDoNot() {
    Detector d = Detector.builder()
        .rule("Lift", r -> r
            .when(channel("weight").above(500))
            .endWhen(channel("weight").below(50)).forSamples(2))
        .listener(rec).build();
    double[] ws = {0, 600, 300, 40, 600, 30, 20};
    for (int i = 0; i < ws.length; i++) d.push(i, Map.of("weight", ws[i]));
    assertEquals(new Aggregates(40, 600, 385, 4), rec.ended.get(0).aggregates("weight"));
  }

  @Test
  void truncatedEndIncludesLastSample() {
    Detector d = Detector.builder()
        .rule("Lift", r -> r.when(channel("weight").above(500)))
        .listener(rec).build();
    d.push(0, Map.of("weight", 600.0));
    d.push(1, Map.of("weight", 800.0));
    d.close();
    assertEquals(new Aggregates(600, 800, 700, 2), rec.ended.get(0).aggregates("weight"));
  }

  @Test
  void confirmedSnapshotHasAggregatesSoFar() {
    List<Event> confirmed = new ArrayList<>();
    Detector d = Detector.builder()
        .rule("Lift", r -> r.when(channel("weight").above(500)).forSamples(2))
        .listener(new EventListener() {
          @Override public void onConfirmed(Event e) { confirmed.add(e); }
        }).build();
    d.push(0, Map.of("weight", 0.0));
    d.push(1, Map.of("weight", 600.0));
    d.push(2, Map.of("weight", 800.0));
    assertEquals(new Aggregates(600, 800, 700, 2), confirmed.get(0).aggregates("weight"));
  }

  @Test
  void attributesAreComputedOnEnd() {
    Detector d = Detector.builder()
        .rule("Lift", r -> r
            .when(channel("weight").above(500))
            .attribute("load", e -> e.aggregates("weight").mean() > 1000 ? "container" : "pallet"))
        .listener(rec).build();
    d.push(0, Map.of("weight", 0.0));
    d.push(1, Map.of("weight", 600.0));
    d.push(2, Map.of("weight", 0.0));
    d.push(3, Map.of("weight", 2000.0));
    d.push(4, Map.of("weight", 0.0));
    assertEquals(Map.of("load", "pallet"), rec.ended.get(0).attributes());
    assertEquals(Map.of("load", "container"), rec.ended.get(1).attributes());
  }
}
