package io.github.romanhaendler.rbed;

import static io.github.romanhaendler.rbed.Conditions.count;
import static io.github.romanhaendler.rbed.Conditions.latest;
import static io.github.romanhaendler.rbed.Conditions.totalDuration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class ExpressionValueTest {
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");
  static final Rule HOT = Rule.named("Hot").when(X.above(5)).forDuration(1_000);

  static List<OptionalDouble> values(Detector d, Expression... expressions) {
    List<OptionalDouble> values = new ArrayList<>();
    for (Expression e : expressions) values.add(d.value(e));
    return values;
  }

  static List<OptionalDouble> of(double... values) {
    List<OptionalDouble> list = new ArrayList<>();
    for (double v : values) list.add(v == v ? OptionalDouble.of(v) : OptionalDouble.empty());
    return list;
  }

  /** Reads watched Expressions from inside every callback, e.g. "confirmed Hot [...]". */
  static final class Reader implements EventListener {
    final List<String> log = new ArrayList<>();
    final Expression[] expressions;
    Detector detector;

    Reader(Expression... expressions) {
      this.expressions = expressions;
    }

    private void read(String phase, Event e) {
      log.add(phase + " " + e.name() + " " + values(detector, expressions));
    }

    @Override public void onPending(Event e) { read("pending", e); }
    @Override public void onConfirmed(Event e) { read("confirmed", e); }
    @Override public void onEnded(Event e) { read("ended", e); }
  }

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
  void anExpressionReadsAsItIsWrittenInJava() {
    Rule run = Rule.named("Run").when(X.above(0));
    assertEquals("x", X.toString());
    assertEquals("x.averageOverSamples(5)", X.averageOverSamples(5).toString());
    assertEquals("x.averageOverDuration(60000).rate()",
        X.averageOverDuration(Duration.ofMinutes(1)).rate().toString());
    assertEquals("x.age().abs()", X.age().abs().toString());
    assertEquals("x.minus(y).times(2.5).dividedBy(y.plus(1.0))",
        X.minus(Y).times(2.5).dividedBy(Y.plus(1)).toString());
    assertEquals("count(Run)", count(run).toString());
    assertEquals("count(Run).within(60000)", count(run).within(60_000).toString());
    assertEquals("totalDuration(Run)", totalDuration(run).toString());
    assertEquals("latest(Run).duration()", latest(run).duration().toString());
    assertEquals("latest(Run).max(x)", latest(run).max(X).toString());
  }

  @Test
  void readingNullFailsLikeWatchingNull() {
    Detector d = Detector.builder().watch(X).build();
    assertThrows(NullPointerException.class, () -> d.value(null));
    assertThrows(NullPointerException.class, () -> Detector.builder().watch((Expression) null));
  }

  @Test
  void readingAnUnwatchedDerivedExpressionNamesIt() {
    Detector d = Detector.builder().build();
    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> d.value(X.averageOverSamples(5)));
    assertTrue(e.getMessage().contains("Expression x.averageOverSamples(5) is not watched"),
        e.getMessage());
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

  @Test
  void expressionsAboutARulesEventsFollowItsEvents() {
    Rule run = Rule.named("Run").when(X.above(0));
    Expression[] watched = {count(run), count(run).within(2_000), totalDuration(run),
        latest(run).duration(), latest(run).max(X)};
    Detector d = Detector.builder().rules(run).watch(watched).build();
    assertEquals(of(0, 0, 0, Double.NaN, Double.NaN), values(d, watched));
    d.push(0, Map.of(X, 1.0)); // Run starts and is Confirmed
    assertEquals(of(1, 1, 0, 0, 1), values(d, watched));
    d.push(1_000, Map.of(X, 3.0));
    assertEquals(of(1, 1, 1_000, 1_000, 3), values(d, watched));
    d.push(1_500, Map.of(X, -1.0)); // Run Ends
    assertEquals(of(1, 1, 1_500, 1_500, 3), values(d, watched));
    d.push(4_000, Map.of(X, 2.0)); // the second Run; the first is out of the 2 s window
    assertEquals(of(2, 1, 1_500, 0, 2), values(d, watched));
    d.advanceTo(5_000);
    assertEquals(of(2, 1, 2_500, 1_000, 2), values(d, watched));
  }

  @Test
  void aWatchedExpressionMayReferToARuleAddedAfterItWasWatched() {
    Rule last = Rule.named("Last").when(X.above(0));
    Expression lasts = count(last);
    Detector d = Detector.builder()
        .watch(lasts)
        .rules(Rule.named("First").when(X.below(0)), last)
        .build();
    d.push(0, Map.of(X, 1.0));
    assertEquals(OptionalDouble.of(1), d.value(lasts));
  }

  @Test
  void aWatchedExpressionReferringToARuleNotAddedIsRejected() {
    Rule other = Rule.named("Other").when(X.above(0));
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().rules(HOT).watch(totalDuration(other)).build());
    assertEquals("Watched Expression totalDuration(Other) references Other, which is not added",
        e.getMessage());
  }

  @Test
  void aWatchedMovingAverageAboveTheRetentionLimitIsRejectedNamingTheExpression() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().retentionLimit(3).watch(X.averageOverSamples(5)).build());
    assertTrue(e.getMessage().startsWith("Watched Expression x.averageOverSamples(5): "
        + "averageOverSamples(5) exceeds the Retention Limit of 3."), e.getMessage());
    assertTrue(!e.getMessage().contains("Rule "), e.getMessage());
  }

  @Test
  void aPulseChannelHasAValueOnlyInTheSampleThatCarriesIt() {
    Channel deploy = Channel.pulse("deploy");
    Expression sinceDeploy = deploy.age();
    Detector d = Detector.builder().watch(deploy, sinceDeploy).build();
    d.push(0, Map.of(deploy, 5.0));
    assertEquals(of(5, 0), values(d, deploy, sinceDeploy));
    d.push(1_000, Map.of(X, 1.0));
    assertEquals(of(Double.NaN, 1_000), values(d, deploy, sinceDeploy));
    d.advanceTo(1_500);
    assertEquals(of(Double.NaN, 1_500), values(d, deploy, sinceDeploy));
    d.push(2_000, Map.of(deploy, 7.0));
    assertEquals(of(7, 0), values(d, deploy, sinceDeploy));
  }

  @Test
  void callbacksReadTheMomentBeingProcessed() {
    Reader reader = new Reader(X, count(HOT), totalDuration(HOT));
    Detector d = Detector.builder().rules(HOT).listener(reader).watch(reader.expressions).build();
    reader.detector = d;
    d.push(0, Map.of(X, 6.0));
    d.push(1_000, Map.of(X, 7.0));
    d.push(2_500, Map.of(X, 1.0));
    assertEquals(List.of("pending Hot " + of(6, 0, 0), "confirmed Hot " + of(7, 1, 1_000),
        "ended Hot " + of(1, 1, 2_500)), reader.log);
  }

  @Test
  void afterCloseValuesAreThoseOfTheLastMoment() {
    Reader reader = new Reader(X, totalDuration(HOT));
    Detector d = Detector.builder().rules(HOT).listener(reader).watch(reader.expressions).build();
    reader.detector = d;
    d.push(0, Map.of(X, 6.0));
    d.push(1_000, Map.of(X, 7.0));
    d.close(); // Hot Ends, truncated, at 1000
    assertEquals("ended Hot " + of(7, 1_000), reader.log.get(2));
    assertEquals(of(7, 1_000), values(d, reader.expressions));
  }

  @Test
  void readsWorkInSamplingRateMode() {
    Expression rate = X.rate();
    Detector d = Detector.builder().samplingRate(2).watch(X, rate).build();
    d.push(Map.of(X, 1.0));
    d.push(Map.of(X, 3.0)); // 500 ms later
    assertEquals(of(3, 4), values(d, X, rate));
  }

  @Test
  void readsWorkDuringAndAfterBatchProcessing() {
    ChannelExpression avg = X.averageOverSamples(2);
    Reader reader = new Reader(avg);
    Detector d = Detector.builder().rules(HOT).listener(reader).watch(avg).build();
    reader.detector = d;
    d.detect(List.of(new Sample(0, Map.of(X, 6.0)), new Sample(1_000, Map.of(X, 8.0)),
        new Sample(2_000, Map.of(X, 2.0))));
    assertEquals(List.of("pending Hot " + of(Double.NaN), "confirmed Hot " + of(7),
        "ended Hot " + of(5)), reader.log);
    assertEquals(OptionalDouble.of(5), d.value(avg));

    Reader sampled = new Reader(avg);
    Detector s = Detector.builder().samplingRate(1).rules(HOT).listener(sampled).watch(avg)
        .build();
    sampled.detector = s;
    s.detectValues(List.of(Map.of(X, 6.0), Map.of(X, 8.0), Map.of(X, 2.0)));
    assertEquals(reader.log, sampled.log);
    assertEquals(OptionalDouble.of(5), s.value(avg));
  }
}
