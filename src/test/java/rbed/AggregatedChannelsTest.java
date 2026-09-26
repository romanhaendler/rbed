package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static rbed.Conditions.channel;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AggregatedChannelsTest {
  final Recorder rec = new Recorder();

  Event lift(Detector.Builder builder) {
    Detector d = builder.listener(rec).build();
    d.push(0, Map.of("weight", 0.0, "height", 1.0, "wind", 3.0));
    d.push(1, Map.of("weight", 600.0, "height", 2.0));
    d.push(2, Map.of("weight", 0.0));
    return rec.ended.get(0);
  }

  @Test
  void channelsReferencedByTheRuleAreAggregated() {
    Event e = lift(Detector.builder().rule("Lift", r -> r
        .when(channel("weight").above(500))
        .endWhen(channel("weight").below(50).and(channel("height").above(-1)))));
    assertEquals(600, e.aggregates("weight").mean());
    assertEquals(2, e.aggregates("height").mean());
    assertEquals(2, e.aggregates().size());
  }

  @Test
  void otherChannelsMustBeRequested() {
    Event e = lift(Detector.builder().rule("Lift", r -> r.when(channel("weight").above(500))));
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> e.aggregates("wind"));
    assertEquals("Rule Lift does not aggregate channel wind; add .aggregate(\"wind\")",
        error.getMessage());
  }

  @Test
  void requestedChannelsAreAggregatedEvenWithoutValues() {
    Event e = lift(Detector.builder().rule("Lift", r -> r
        .when(channel("weight").above(500)).aggregate("wind", "rain")));
    assertEquals(3, e.aggregates("wind").mean());
    assertEquals(0, e.aggregates("rain").count());
  }
}
