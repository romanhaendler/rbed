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
    Rule run = Rule.named("Run").when(X.above(0));
    Detector d = Detector.builder()
        .rules(run)
        .rules(Rule.named("TooManyStarts").when(count(run).within(40).above(2)))
        .listener(rec).build();
    // Runs Confirmed at 10, 30, 50 and 130
    push(d, new long[] {0, 10, 20, 30, 40, 50, 60, 100, 130, 140}, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0);
    // at 50: 10, 30, 50 are within 40 ms; at 60, 10 has left the window
    assertEquals(List.of("ended TooManyStarts 50..60"), rec.endedLines().stream()
        .filter(line -> line.contains("TooMany")).toList());
  }

  @Test
  void countWithoutWindowCountsSinceStart() {
    Rule rep = Rule.named("Rep").when(X.above(0));
    Detector d = Detector.builder()
        .rules(rep)
        .rules(Rule.named("ThirdRep").when(count(rep).above(2)))
        .listener(rec).build();
    push(d, new long[] {0, 10, 20, 30, 40, 1000}, 0, 1, 0, 1, 0, 1);
    assertEquals("confirmed ThirdRep 1000", rec.log.get(rec.log.size() - 1));
  }

  @Test
  void countIncludesHistory() {
    Rule run = Rule.named("Run").when(X.above(0));
    Detector d = Detector.builder()
        .rules(run)
        .rules(Rule.named("Twice").when(count(run).within(40).above(1)))
        .history(List.of(ended("Run", -20, -10, -5)))
        .listener(rec).build();
    push(d, new long[] {0, 10}, 0, 1);
    assertEquals("confirmed Twice 10", rec.log.get(rec.log.size() - 1));
  }

  @Test
  void totalDurationSumsEndedAndRunningEvents() {
    Rule excursion = Rule.named("Excursion").when(X.above(8));
    Detector d = Detector.builder()
        .rules(excursion)
        .rules(Rule.named("Limit").when(totalDuration(excursion).above(25)))
        .listener(rec).build();
    // Excursions 10..20 (10 ms) and from 30: running total at 45 is 10 + 15 = 25
    push(d, new long[] {0, 10, 20, 30, 40, 45, 46}, 5, 9, 5, 9, 9, 9, 9);
    assertEquals("confirmed Limit 46", rec.log.get(rec.log.size() - 1));
  }

  @Test
  void totalDurationIncludesHistory() {
    Rule excursion = Rule.named("Excursion").when(X.above(8));
    Detector d = Detector.builder()
        .rules(excursion)
        .rules(Rule.named("Limit").when(totalDuration(excursion).above(25)))
        .history(List.of(ended("Excursion", -100, -90, -70)))
        .listener(rec).build();
    push(d, new long[] {0}, 5);
    assertEquals(List.of("pending Limit 0", "confirmed Limit 0"), rec.log);
  }

  @Test
  void referencesMustBeToEarlierRules() {
    Rule b = Rule.named("B").when(X.above(0));
    Rule a = Rule.named("A").when(count(b).above(1));
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().rules(a, b).build());
    Rule self = Rule.named("A").when(totalDuration(Rule.named("A")).above(1));
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().rules(self).build());
  }
}
