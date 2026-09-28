package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BatchTest {
  static final Channel WEIGHT = Channel.named("weight");

  final Recorder rec = new Recorder();

  Detector.Builder lift() {
    return Detector.builder()
        .rules(Rule.named("Lift").when(WEIGHT.above(500)))
        .rules(Rule.named("Heavy").when(WEIGHT.above(900)))
        .listener(rec);
  }

  static String describe(Event e) {
    return e.name() + " " + e.start() + ".." + e.end().getAsLong();
  }

  @Test
  void detectReturnsEndedEventsInOrderTheyEndedAndCloses() {
    List<Event> events = lift().build().detect(List.of(
        new Sample(0, Map.of(WEIGHT, 0.0)),
        new Sample(10, Map.of(WEIGHT, 1000.0)),
        new Sample(20, Map.of(WEIGHT, 600.0)),
        new Sample(30, Map.of(WEIGHT, 0.0)),
        new Sample(40, Map.of(WEIGHT, 700.0))));
    assertEquals(List.of("Heavy 10..20", "Lift 10..30", "Lift 40..40"),
        events.stream().map(BatchTest::describe).toList());
    assertEquals(true, events.get(2).truncatedEnd());
    assertEquals(3, rec.ended.size()); // listener is still called
  }

  @Test
  void samplingRateDerivesTimestamps() {
    List<Event> events = lift().samplingRate(4).build().detectValues(List.of(
        Map.of(WEIGHT, 0.0),
        Map.of(WEIGHT, 600.0),
        Map.of(WEIGHT, 600.0),
        Map.of(WEIGHT, 0.0)));
    assertEquals(List.of("Lift 250..750"), events.stream().map(BatchTest::describe).toList());
  }

  @Test
  void streamingWithSamplingRate() {
    Detector d = lift().samplingRate(3).build();
    d.push(Map.of(WEIGHT, 0.0));
    d.push(Map.of(WEIGHT, 600.0));  // round(1000 / 3) = 333
    assertEquals(List.of("pending Lift 333", "confirmed Lift 333"), rec.log);
  }

  @Test
  void misuseIsRejected() {
    assertThrows(IllegalStateException.class, () -> lift().build().push(Map.of(WEIGHT, 0.0)));
    Detector sampled = lift().samplingRate(1).build();
    sampled.push(Map.of(WEIGHT, 0.0));
    assertThrows(IllegalStateException.class, () -> sampled.push(5000, Map.of(WEIGHT, 0.0)));
    Detector used = lift().build();
    used.push(0, Map.of(WEIGHT, 0.0));
    assertThrows(IllegalStateException.class, () -> used.detect(List.of()));
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().samplingRate(0));
  }

  @Test
  void samplingRateIsLimitedToMillisecondResolution() {
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().samplingRate(1001));
    Detector d = lift().samplingRate(1000).build();
    d.push(Map.of(WEIGHT, 0.0));
    d.push(Map.of(WEIGHT, 600.0));
    assertEquals(List.of("pending Lift 1", "confirmed Lift 1"), rec.log);
  }
}
