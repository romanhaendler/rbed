package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ExpressionValueTest {
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");

  @Test
  void aWatchedChannelReadsItsLatestValueAndIsEmptyBeforeTheFirstSample() {
    Detector d = Detector.builder().watch(X).build();
    assertEquals(OptionalDouble.empty(), d.value(X));
    d.push(0, Map.of(X, 3.5));
    assertEquals(OptionalDouble.of(3.5), d.value(X));
  }

  @Test
  void aChannelIsMatchedByName() {
    Detector d = Detector.builder().watch(Channel.named("x")).build();
    d.push(0, Map.of(X, 1.0));
    assertEquals(OptionalDouble.of(1), d.value(Channel.named("x")));
  }

  @Test
  void aMovingAverageOverSamplesIsEmptyDuringWarmUp() {
    ChannelExpression avg = X.averageOverSamples(3);
    Detector d = Detector.builder().watch(avg).build();
    List<OptionalDouble> read = new ArrayList<>();
    double[] xs = {1, 2, 6, 10};
    for (int i = 0; i < xs.length; i++) {
      d.push(i * 1000L, Map.of(X, xs[i]));
      read.add(d.value(avg));
    }
    assertEquals(List.of(OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.of(3),
        OptionalDouble.of(6)), read);
  }

  @Test
  void aMovingAverageOverADurationIsEmptyUntilItsValuesSpanThatLong() {
    ChannelExpression avg = X.averageOverDuration(2_000);
    Detector d = Detector.builder().watch(avg).build();
    d.push(0, Map.of(X, 2.0));
    d.push(1_000, Map.of(X, 4.0));
    assertEquals(OptionalDouble.empty(), d.value(avg));
    d.push(2_000, Map.of(X, 6.0));
    assertEquals(OptionalDouble.of(4), d.value(avg));
  }

  @Test
  void rateIsTheChangePerSecond() {
    Expression rate = X.rate();
    Detector d = Detector.builder().watch(rate).build();
    d.push(0, Map.of(X, 10.0));
    assertEquals(OptionalDouble.empty(), d.value(rate));
    d.push(500, Map.of(X, 15.0));
    assertEquals(OptionalDouble.of(10), d.value(rate));
  }

  @Test
  void ageGrowsWithTimeAdvances() {
    Expression age = X.age();
    Detector d = Detector.builder().watch(age).build();
    d.advanceTo(100);
    assertEquals(OptionalDouble.empty(), d.value(age));
    d.push(200, Map.of(X, 1.0));
    assertEquals(OptionalDouble.of(0), d.value(age));
    d.advanceTo(1_200);
    assertEquals(OptionalDouble.of(1_000), d.value(age));
  }

  @Test
  void arithmeticBetweenChannelsIsEmptyWhileAnOperandIs() {
    Expression diff = X.minus(Y).times(2);
    Detector d = Detector.builder().watch(diff).build();
    d.push(0, Map.of(X, 5.0));
    assertEquals(OptionalDouble.empty(), d.value(diff));
    d.push(1, Map.of(Y, 2.0));
    assertEquals(OptionalDouble.of(6), d.value(diff));
  }

  @Test
  void readingAnUnwatchedExpressionFailsNamingIt() {
    Detector d = Detector.builder().watch(X.rate()).build();
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> d.value(Y));
    assertTrue(e.getMessage().contains("Expression y is not watched"), e.getMessage());
    // every Expression but a Channel is matched by identity
    assertThrows(IllegalArgumentException.class, () -> d.value(X.rate()));
  }

  @Test
  void watchingTwiceAndWatchingOneExpressionTwiceIsHarmless() {
    Expression rate = X.rate();
    Detector d = Detector.builder().watch(X, rate).watch(rate, X, Channel.named("x")).build();
    assertEquals(1, d.retainedValues()); // one Channel, no window
    d.push(0, Map.of(X, 1.0));
    d.push(1_000, Map.of(X, 3.0));
    assertEquals(OptionalDouble.of(3), d.value(X));
    assertEquals(OptionalDouble.of(2), d.value(rate));
  }

  @Test
  void aWatchedExpressionNoRuleUsesKeepsItsChannelsAndWindow() {
    Detector unwatched = Detector.builder().rules(Rule.named("R").when(X.above(0))).build();
    Detector watched = Detector.builder()
        .rules(Rule.named("R").when(X.above(0)))
        .watch(Y.averageOverSamples(4))
        .build();
    for (int i = 0; i < 10; i++) {
      unwatched.push(i, Map.of(X, 1.0, Y, 1.0));
      watched.push(i, Map.of(X, 1.0, Y, 1.0));
    }
    assertEquals(1, unwatched.retainedValues()); // x
    assertEquals(1 + 1 + 4, watched.retainedValues()); // x, y and y's window
  }

  @Test
  void watchingChangesNoEvent() {
    Recorder plain = new Recorder();
    Recorder observed = new Recorder();
    Detector.Builder[] builders = {Detector.builder().listener(plain),
        Detector.builder().listener(observed)
            .watch(X, X.averageOverSamples(2), X.averageOverDuration(1_000), X.rate(), X.age(),
                X.averageOverSamples(2).rate())};
    for (Detector.Builder b : builders) {
      Detector d = b
          .rules(Rule.named("Smooth").when(X.averageOverSamples(2).rate().above(5)))
          .rules(Rule.named("High").when(X.averageOverDuration(1_000).above(12)).forSamples(2))
          .build();
      double[] xs = {0, 10, 0, 10, 20, 30, 5, 5};
      for (int i = 0; i < xs.length; i++) d.push(i * 1000L, Map.of(X, xs[i]));
      d.close();
    }
    assertEquals(List.of("pending Smooth 4000", "confirmed Smooth 4000", "pending High 4000",
        "confirmed High 4000", "ended Smooth 4000..6000", "ended High 4000..7000"), plain.log);
    assertEquals(plain.log, observed.log);
  }
}
