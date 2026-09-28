package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static rbed.Conditions.count;
import static rbed.Conditions.totalDuration;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class EventCountAndDurationTest {
  static final Channel X = Channel.named("x");

  final Recorder rec = new Recorder();

  static Event ended(String name, long start, long confirmed, long end) {
    return new Event(name, start, OptionalLong.of(confirmed), OptionalLong.of(end), false, false,
        Map.of());
  }

  void push(Detector d, long[] ts, double... values) {
    for (int i = 0; i < ts.length; i++) d.push(ts[i], Map.of(X, values[i]));
  }

  @Test
  void countWithinASlidingWindow() {
    Detector d = Detector.builder()
        .rule("Run", r -> r.when(X.above(0)))
        .rule("TooManyStarts", r -> r.when(count("Run").within(40).above(2)))
        .listener(rec).build();
    // Runs Confirmed at 10, 30, 50 and 130
    push(d, new long[] {0, 10, 20, 30, 40, 50, 60, 100, 130, 140}, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0);
    // at 50: 10, 30, 50 are within 40 ms; at 60, 10 has left the window
    assertEquals(List.of("ended TooManyStarts 50..60"), rec.endedLines().stream()
        .filter(line -> line.contains("TooMany")).toList());
  }

  @Test
  void countWithoutWindowCountsSinceStart() {
    Detector d = Detector.builder()
        .rule("Rep", r -> r.when(X.above(0)))
        .rule("ThirdRep", r -> r.when(count("Rep").above(2)))
        .listener(rec).build();
    push(d, new long[] {0, 10, 20, 30, 40, 1000}, 0, 1, 0, 1, 0, 1);
    assertEquals("confirmed ThirdRep 1000", rec.log.get(rec.log.size() - 1));
  }

  @Test
  void countIncludesHistory() {
    Detector d = Detector.builder()
        .rule("Run", r -> r.when(X.above(0)))
        .rule("Twice", r -> r.when(count("Run").within(40).above(1)))
        .history(List.of(ended("Run", -20, -10, -5)))
        .listener(rec).build();
    push(d, new long[] {0, 10}, 0, 1);
    assertEquals("confirmed Twice 10", rec.log.get(rec.log.size() - 1));
  }

  @Test
  void totalDurationSumsEndedAndRunningEvents() {
    Detector d = Detector.builder()
        .rule("Excursion", r -> r.when(X.above(8)))
        .rule("Limit", r -> r.when(totalDuration("Excursion").above(25)))
        .listener(rec).build();
    // Excursions 10..20 (10 ms) and from 30: running total at 45 is 10 + 15 = 25
    push(d, new long[] {0, 10, 20, 30, 40, 45, 46}, 5, 9, 5, 9, 9, 9, 9);
    assertEquals("confirmed Limit 46", rec.log.get(rec.log.size() - 1));
  }

  @Test
  void totalDurationIncludesHistory() {
    Detector d = Detector.builder()
        .rule("Excursion", r -> r.when(X.above(8)))
        .rule("Limit", r -> r.when(totalDuration("Excursion").above(25)))
        .history(List.of(ended("Excursion", -100, -90, -70)))
        .listener(rec).build();
    push(d, new long[] {0}, 5);
    assertEquals(List.of("pending Limit 0", "confirmed Limit 0"), rec.log);
  }

  @Test
  void referencesMustBeToEarlierRules() {
    assertThrows(IllegalArgumentException.class, () -> Detector.builder()
        .rule("A", r -> r.when(count("B").above(1)))
        .rule("B", r -> r.when(X.above(0))).build());
    assertThrows(IllegalArgumentException.class, () -> Detector.builder()
        .rule("A", r -> r.when(totalDuration("A").above(1))).build());
  }
}
