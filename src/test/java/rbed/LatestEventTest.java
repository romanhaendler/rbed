package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rbed.Conditions.during;
import static rbed.Conditions.latest;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class LatestEventTest {
  static final Channel WEIGHT = Channel.named("weight");
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");
  static final Channel Z = Channel.named("z");

  final Recorder rec = new Recorder();

  @Test
  void partialSetDownComparesWithTheRunningLift() {
    Detector d = Detector.builder()
        .rule("LoadLifted", r -> r
            .when(WEIGHT.above(500))
            .endWhen(WEIGHT.below(50)))
        .rule("PartialSetDown", r -> r
            .when(during("LoadLifted")
                .and(WEIGHT.below(latest("LoadLifted").max(WEIGHT).times(0.5))))
            .forSamples(2))
        .listener(rec).build();
    double[] weights = {0, 1000, 1000, 1000, 400, 400, 0};
    for (int i = 0; i < weights.length; i++) d.push(i, Map.of(WEIGHT, weights[i]));
    // half of the lifted 1000 kg set down at 4; everything set down at 6
    assertEquals(List.of("ended LoadLifted 1..6", "ended PartialSetDown 4..6"), rec.endedLines());
  }

  @Test
  void valuesOfTheEndedEventRemainUntilTheNextIsConfirmed() {
    Detector d = Detector.builder()
        .rule("A", r -> r.when(Y.above(0)))
        .rule("B", r -> r.when(X.above(latest("A").mean(Y))))
        .listener(rec).build();
    d.push(0, Map.of(X, 100.0, Y, 0.0));  // no A yet: undefined, no B
    d.push(1, Map.of(X, 0.0, Y, 10.0));
    d.push(2, Map.of(Y, 20.0));
    d.push(3, Map.of(Y, 0.0));              // A Ended with mean 15
    d.push(4, Map.of(X, 16.0));
    d.push(5, Map.of(X, 14.0));
    assertEquals(List.of("ended A 1..3", "ended B 4..5"), rec.endedLines());
  }

  @Test
  void durationGrowsWhileRunningAndStaysAfterTheEnd() {
    Detector d = Detector.builder()
        .rule("A", r -> r.when(Y.above(0)))
        .rule("Long", r -> r.when(latest("A").duration().above(1500)))
        .listener(rec).build();
    for (long t = 0; t <= 5000; t += 1000) {
      d.push(t, Map.of(Y, t >= 1000 && t < 4000 ? 1.0 : 0.0));
    }
    // A runs from 1000; its duration exceeds 1500 at 3000 and stays 3000 after it Ended at 4000
    assertEquals(List.of("pending Long 3000", "confirmed Long 3000"), rec.log.stream()
        .filter(line -> line.contains("Long")).toList());
  }

  @Test
  void historyGivesDurationButNoChannelValues() {
    Event a = new Event("A", -2000, OptionalLong.of(-1900), OptionalLong.of(-100), false, false,
        Map.of());
    Detector d = Detector.builder()
        .rule("Long", r -> r.when(latest("A").duration().above(1500)))
        .rule("Mean", r -> r.when(latest("A").mean(Y).above(-1e9).or(
            latest("A").mean(Y).above(-1e9).not())))
        .history(List.of(a))
        .listener(rec).build();
    d.push(0, Map.of(Y, 1.0));
    assertEquals(List.of("pending Long 0", "confirmed Long 0"), rec.log);
  }

  @Test
  void referencedChannelsAreAggregatedByTheReferencedRule() {
    Detector d = Detector.builder()
        .rule("A", r -> r.when(Y.above(0)))
        .rule("B", r -> r.when(latest("A").mean(Z).above(0)))
        .listener(rec).build();
    d.push(0, Map.of(Y, 1.0, Z, 5.0));
    d.push(1, Map.of(Y, 0.0));
    assertEquals(5, rec.ended.get(0).aggregates(Z).mean());
    assertTrue(rec.log.contains("confirmed B 0"));
  }

  @Test
  void referencesMustBeToEarlierRules() {
    assertThrows(IllegalArgumentException.class, () -> Detector.builder()
        .rule("A", r -> r.when(latest("B").duration().above(0)))
        .rule("B", r -> r.when(X.above(0))).build());
  }

  @Test
  void readingLatestValuesDoesNotAggregateTheChannelInTheReadingRule() {
    Detector d = Detector.builder()
        .rule("A", r -> r.when(Y.above(0)))
        .rule("B", r -> r.when(latest("A").mean(Z).above(0).and(Y.above(0))))
        .listener(rec).build();
    d.push(0, Map.of(Y, 1.0, Z, 5.0));
    d.push(1, Map.of(Y, 0.0));
    Event b = rec.ended.get(1);
    assertEquals("B", b.name());
    assertThrows(IllegalArgumentException.class, () -> b.aggregates(Z));
  }
}
