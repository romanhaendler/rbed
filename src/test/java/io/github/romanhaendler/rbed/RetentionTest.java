package io.github.romanhaendler.rbed;

import static io.github.romanhaendler.rbed.Conditions.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

  // Retention Limit on duration windows

  /** Pushes x = timestamp for timestamps 0..99 through a 50 ms window with the given limit. */
  private static Detector denseStream(int limit, Recorder rec) {
    Detector d = Detector.builder().retentionLimit(limit).listener(rec)
        .rules(Rule.named("Hi").when(X.averageOverDuration(50).above(94.4)),
            Rule.named("TooHi").when(X.averageOverDuration(50).above(94.6)))
        .build();
    for (int i = 0; i < 100; i++) d.push(i, Map.of(X, (double) i));
    return d;
  }

  @Test
  void durationWindowStopsAtTheLimitAndCountsDrops() {
    Detector d = denseStream(10, new Recorder());
    assertEquals(1 + 2 * 10, d.retainedValues()); // x plus two windows
    assertEquals(2 * 90, d.droppedValues());
  }

  @Test
  void averageIsTheMeanOfTheMostRecentLimitValues() {
    Recorder rec = new Recorder();
    denseStream(10, rec); // the last 10 values are 90..99, mean 94.5
    assertEquals(List.of("confirmed Hi 99"), rec.log.stream().filter(l -> l.startsWith("conf")).toList());
  }

  @Test
  void belowTheLimitNothingChanges() {
    Recorder limited = new Recorder();
    Recorder unlimited = new Recorder();
    Detector d = denseStream(1000, limited);
    Detector.Builder b = Detector.builder().listener(unlimited)
        .rules(Rule.named("Hi").when(X.averageOverDuration(50).above(94.4)),
            Rule.named("TooHi").when(X.averageOverDuration(50).above(94.6)));
    Detector u = b.build();
    for (int i = 0; i < 100; i++) u.push(i, Map.of(X, (double) i));
    assertEquals(unlimited.log, limited.log);
    assertEquals(0, d.droppedValues());
  }

  @Test
  void firstDropLogsOneWarningNamingRuleAndLimit() {
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger("io.github.romanhaendler.rbed");
    List<java.util.logging.LogRecord> records = new ArrayList<>();
    java.util.logging.Handler handler = new java.util.logging.Handler() {
      @Override public void publish(java.util.logging.LogRecord r) { records.add(r); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    logger.addHandler(handler);
    try {
      denseStream(10, new Recorder());
    } finally {
      logger.removeHandler(handler);
    }
    assertEquals(1, records.size());
    assertEquals(java.util.logging.Level.WARNING, records.get(0).getLevel());
    String message = records.get(0).getMessage();
    assertTrue(message.contains("Hi") && message.contains("10") && message.contains("retentionLimit"), message);
  }

  /** The messages logged to the library's logger while {@code work} runs. */
  private static List<String> warnings(Runnable work) {
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger("io.github.romanhaendler.rbed");
    List<String> messages = new ArrayList<>();
    java.util.logging.Handler handler = new java.util.logging.Handler() {
      @Override public void publish(java.util.logging.LogRecord r) { messages.add(r.getMessage()); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    logger.addHandler(handler);
    try {
      work.run();
    } finally {
      logger.removeHandler(handler);
    }
    return messages;
  }

  @Test
  void theWarningNamesWhatOwnsTheFullWindow() {
    ChannelExpression avg = X.averageOverDuration(100);
    assertEquals(List.of("Rule Hi: a window reached the Retention Limit of 3 entries; its oldest"
        + " entries are dropped, so results of Rule Hi deviate from an unlimited window."
        + " Raise the limit with Detector.builder().retentionLimit(...)."), warnings(() -> {
          Detector d = Detector.builder().retentionLimit(3)
              .rules(Rule.named("Hi").when(avg.above(0))).build();
          for (int i = 0; i < 5; i++) d.push(i, Map.of(X, 1.0));
        }));
    assertEquals(List.of("Watched Expression x.averageOverDuration(100): a window reached the"
        + " Retention Limit of 3 entries; its oldest entries are dropped, so results of Watched"
        + " Expression x.averageOverDuration(100) deviate from an unlimited window."
        + " Raise the limit with Detector.builder().retentionLimit(...)."), warnings(() -> {
          Detector d = Detector.builder().retentionLimit(3).watch(avg).build();
          for (int i = 0; i < 5; i++) d.push(i, Map.of(X, 1.0));
        }));
  }

  @Test
  void retentionLimitBelowOneIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().retentionLimit(0));
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().retentionLimit(-5));
  }

  // Retention Limit on Event counts

  private static Detector runs(int limit, int runs, List<Event> history) {
    Rule run = Rule.named("Run").when(X.above(0));
    Detector d = Detector.builder().retentionLimit(limit).history(history)
        .rules(run, Rule.named("Many").when(count(run).within(1000).above(2))).build();
    for (int i = 0; i < runs; i++) {
      d.push(2 * i, Map.of(X, 1.0));
      d.push(2 * i + 1, Map.of(X, 0.0));
    }
    return d;
  }

  @Test
  void countWithinIsCappedAtTheLimitAndCountsDrops() {
    Detector d = runs(3, 10, List.of());
    assertEquals(1 + 3, d.retainedValues()); // x plus three timestamps
    assertEquals(7, d.droppedValues());
  }

  @Test
  void countAboveTheLimitIsNeverReached() {
    Recorder rec = new Recorder();
    Rule run = Rule.named("Run").when(X.above(0));
    Detector d = Detector.builder().retentionLimit(3).listener(rec)
        .rules(run, Rule.named("Four").when(count(run).within(1000).above(3))).build();
    for (int i = 0; i < 10; i++) {
      d.push(2 * i, Map.of(X, 1.0));
      d.push(2 * i + 1, Map.of(X, 0.0));
    }
    assertTrue(rec.log.stream().noneMatch(l -> l.contains("Four")), rec.log.toString());
  }

  @Test
  void historyBeyondTheLimitIsDroppedAndCounted() {
    List<Event> history = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      long t = -100 + 10 * i;
      history.add(new Event("Run", t, java.util.OptionalLong.of(t), java.util.OptionalLong.of(t + 1),
          false, false, Map.of()));
    }
    Detector d = runs(3, 0, history);
    assertEquals(2, d.droppedValues());
    assertEquals(1 + 3, d.retainedValues());
  }

  @Test
  void countDropWarnsOnceNamingTheRuleUsingTheCount() {
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger("io.github.romanhaendler.rbed");
    List<java.util.logging.LogRecord> records = new ArrayList<>();
    java.util.logging.Handler handler = new java.util.logging.Handler() {
      @Override public void publish(java.util.logging.LogRecord r) { records.add(r); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    logger.addHandler(handler);
    try {
      runs(3, 10, List.of());
    } finally {
      logger.removeHandler(handler);
    }
    assertEquals(1, records.size());
    assertTrue(records.get(0).getMessage().contains("count of Run"), records.get(0).getMessage());
  }

  @Test
  void countBelowTheLimitIsUnchanged() {
    Detector d = runs(1000, 10, List.of());
    assertEquals(0, d.droppedValues());
    assertEquals(1 + 10, d.retainedValues());
  }

  @Test
  void averageOverSamplesAboveTheRetentionLimitFailsInBuild() {
    Rule rule = Rule.named("Big").when(X.averageOverSamples(11).above(0));
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().retentionLimit(10).rules(rule).build());
    assertTrue(e.getMessage().contains("Big") && e.getMessage().contains("11"), e.getMessage());
  }

  @Test
  void averageOverSamplesAtOrBelowTheLimitBuilds() {
    Detector.builder().retentionLimit(10).rules(Rule.named("R").when(X.averageOverSamples(10).above(0))).build();
    Detector.builder().retentionLimit(11).rules(Rule.named("Big").when(X.averageOverSamples(11).above(0))).build();
  }

  @Test
  void sameRuleBuildsAfterRaisingTheLimit() {
    Rule rule = Rule.named("Big").when(X.averageOverSamples(11).above(0));
    assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().retentionLimit(10).rules(rule).build());
    Detector.builder().retentionLimit(11).rules(rule).build();
  }

  @Test
  void twoWindowsDroppingLogExactlyOneWarning() {
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger("io.github.romanhaendler.rbed");
    List<java.util.logging.LogRecord> records = new ArrayList<>();
    java.util.logging.Handler handler = new java.util.logging.Handler() {
      @Override public void publish(java.util.logging.LogRecord r) { records.add(r); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    logger.addHandler(handler);
    try {
      denseStream(10, new Recorder()); // two windows both drop
    } finally {
      logger.removeHandler(handler);
    }
    assertEquals(1, records.size());
  }

  @Test
  void retainedValuesAreCorrectAfterLimitDropThenTimeEviction() {
    Detector d = Detector.builder().retentionLimit(3)
        .rules(Rule.named("R").when(X.averageOverDuration(10).above(0))).build();
    for (int i = 0; i < 6; i++) d.push(i, Map.of(X, 1.0));
    assertEquals(1 + 3, d.retainedValues());
    d.push(100, Map.of(X, 1.0)); // the older entries leave by time
    assertEquals(1 + 1, d.retainedValues());
    assertEquals(3, d.droppedValues()); // only the 3 while filling; leaving by time is not a drop
  }

  @Test
  void aGapWithAFullWindowLogsNoWarning() {
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger("io.github.romanhaendler.rbed");
    List<java.util.logging.LogRecord> records = new ArrayList<>();
    java.util.logging.Handler handler = new java.util.logging.Handler() {
      @Override public void publish(java.util.logging.LogRecord r) { records.add(r); }
      @Override public void flush() {}
      @Override public void close() {}
    };
    logger.addHandler(handler);
    Detector d = Detector.builder().retentionLimit(3)
        .rules(Rule.named("R").when(X.averageOverDuration(10).above(0))).build();
    try {
      for (int i = 0; i < 3; i++) d.push(i, Map.of(X, 1.0));
      d.push(100, Map.of(X, 1.0));
    } finally {
      logger.removeHandler(handler);
    }
    assertEquals(0, records.size());
    assertEquals(0, d.droppedValues());
  }
}
