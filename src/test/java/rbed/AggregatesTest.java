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
        .rule("Lift", r -> r.when(channel("weight").above(500)).aggregate("height"))
        .listener(rec).build();
    d.push(0, Map.of("weight", 0.0, "height", 1.0));
    d.push(1, Map.of("weight", 600.0));
    d.push(2, Map.of("weight", 700.0, "height", 5.0));
    d.push(3, Map.of("weight", 650.0));
    d.push(4, Map.of("weight", 100.0, "height", 9.0));
    Event e = rec.ended.get(0);
    assertEquals(Recorder.stats(600, 700, 650, 3), Recorder.stats(e.aggregates("weight")));
    // height is held at 1 for the Sample at 1, then 5 for the Samples at 2 and 3
    assertEquals(Recorder.stats(1, 5, 11.0 / 3, 3), Recorder.stats(e.aggregates("height")));
  }

  @Test
  void requestedChannelWithoutValuesHasEmptyAggregates() {
    Detector d = Detector.builder()
        .rule("Lift", r -> r.when(channel("weight").above(500)).aggregate("nope"))
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
    assertEquals(Recorder.stats(40, 600, 385, 4), Recorder.stats(rec.ended.get(0).aggregates("weight")));
  }

  @Test
  void truncatedEndIncludesLastSample() {
    Detector d = Detector.builder()
        .rule("Lift", r -> r.when(channel("weight").above(500)))
        .listener(rec).build();
    d.push(0, Map.of("weight", 600.0));
    d.push(1, Map.of("weight", 800.0));
    d.close();
    assertEquals(Recorder.stats(600, 800, 700, 2), Recorder.stats(rec.ended.get(0).aggregates("weight")));
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
    assertEquals(Recorder.stats(600, 800, 700, 2), Recorder.stats(confirmed.get(0).aggregates("weight")));
  }
}
