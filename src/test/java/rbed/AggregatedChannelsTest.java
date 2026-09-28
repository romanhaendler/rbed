package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AggregatedChannelsTest {
  static final Channel HEIGHT = Channel.named("height");
  static final Channel RAIN = Channel.named("rain");
  static final Channel WEIGHT = Channel.named("weight");
  static final Channel WIND = Channel.named("wind");

  final Recorder rec = new Recorder();

  Event lift(Detector.Builder builder) {
    Detector d = builder.listener(rec).build();
    d.push(0, Map.of(WEIGHT, 0.0, HEIGHT, 1.0, WIND, 3.0));
    d.push(1, Map.of(WEIGHT, 600.0, HEIGHT, 2.0));
    d.push(2, Map.of(WEIGHT, 0.0));
    return rec.ended.get(0);
  }

  @Test
  void channelsReferencedByTheRuleAreAggregated() {
    Event e = lift(Detector.builder().rules(Rule.named("Lift")
        .when(WEIGHT.above(500))
        .endWhen(WEIGHT.below(50).and(HEIGHT.above(-1)))));
    assertEquals(600, e.aggregates(WEIGHT).mean());
    assertEquals(2, e.aggregates(HEIGHT).mean());
    assertEquals(2, e.aggregates().size());
  }

  @Test
  void otherChannelsMustBeRequested() {
    Event e = lift(Detector.builder().rules(Rule.named("Lift").when(WEIGHT.above(500))));
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> e.aggregates(WIND));
    assertEquals("Rule Lift does not aggregate channel wind; add it with aggregate(...)",
        error.getMessage());
  }

  @Test
  void requestedChannelsAreAggregatedEvenWithoutValues() {
    Event e = lift(Detector.builder().rules(Rule.named("Lift")
        .when(WEIGHT.above(500)).aggregate(WIND, RAIN)));
    assertEquals(3, e.aggregates(WIND).mean());
    assertEquals(0, e.aggregates(RAIN).count());
  }
}
