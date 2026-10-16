package io.github.romanhaendler.rbed;

import static io.github.romanhaendler.rbed.Conditions.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetentionTest {
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");

  @Test
  void unusedChannelsLeaveNoTrace() {
    Detector d = Detector.builder().rules(Rule.named("R").when(X.above(0))).build();
    d.push(0, Map.of(X, 1.0));
    long before = d.retainedValues();
    for (int i = 1; i <= 1000; i++) d.push(i, Map.of(X, 1.0, Channel.named("unused" + i), 5.0));
    assertEquals(before, d.retainedValues());
  }

  @Test
  void invalidValueOfUnusedChannelIsStillRejectedWithoutTrace() {
    Detector d = Detector.builder().rules(Rule.named("R").when(X.above(0))).build();
    d.push(0, Map.of(X, 1.0));
    Map<Channel, Double> bad = new HashMap<>();
    bad.put(X, 2.0);
    bad.put(Channel.named("unused"), Double.NaN);
    assertThrows(IllegalArgumentException.class, () -> d.push(1, bad));
    d.push(1, Map.of(X, 3.0)); // timestamp 1 was not consumed
  }

  @Test
  void channelsUsedOnlyThroughAggregatesStillWork() {
    Recorder rec = new Recorder();
    Detector d = Detector.builder().listener(rec)
        .rules(Rule.named("R").when(X.above(0)).aggregate(Y)).build();
    d.push(0, Map.of(X, 1.0, Y, 4.0));
    d.push(1, Map.of(X, 1.0, Y, 8.0));
    d.push(2, Map.of(X, -1.0, Y, 100.0));
    d.close();
    assertEquals(1, rec.endedLines().size());
    assertEquals(2, d.retainedValues()); // x and y
  }

  @Test
  void retainedValuesFollowADurationWindow() {
    Detector d = Detector.builder()
        .rules(Rule.named("R").when(X.averageOverDuration(3).above(0))).build();
    long base = d.retainedValues();
    d.push(0, Map.of(X, 1.0));
    assertEquals(base + 1, d.retainedValues());
    d.push(1, Map.of(X, 1.0));
    d.push(2, Map.of(X, 1.0));
    assertEquals(base + 3, d.retainedValues());
    d.push(10, Map.of(X, 1.0)); // everything older than 3 ms leaves
    assertEquals(base + 1, d.retainedValues());
  }

  @Test
  void retainedValuesCountASampleWindow() {
    Detector d = Detector.builder()
        .rules(Rule.named("R").when(X.averageOverSamples(4).above(0))).build();
    long base = d.retainedValues();
    for (int i = 0; i < 10; i++) d.push(i, Map.of(X, 1.0));
    assertEquals(base + 4, d.retainedValues());
  }

  @Test
  void retainedValuesCountEventTimestamps() {
    Rule run = Rule.named("Run").when(X.above(0));
    Detector d = Detector.builder()
        .rules(run, Rule.named("Many").when(count(run).within(100).above(5))).build();
    long base = d.retainedValues();
    d.push(0, Map.of(X, 1.0)); // Run confirmed: one timestamp
    assertEquals(base + 1, d.retainedValues());
    d.push(1, Map.of(X, -1.0));
    d.push(2, Map.of(X, 1.0));
    assertEquals(base + 2, d.retainedValues());
    d.push(500, Map.of(X, -1.0)); // both left the 100 ms window
    assertEquals(base, d.retainedValues());
  }
}
