package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static rbed.Conditions.after;
import static rbed.Conditions.during;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class SequenceTest {
  static final Channel A = Channel.named("a");
  static final Channel F = Channel.named("f");
  static final Channel M = Channel.named("m");
  static final Channel WEIGHT = Channel.named("weight");
  static final Channel X = Channel.named("x");

  final Recorder rec = new Recorder();

  List<String> ended() {
    return rec.endedLines();
  }

  @Test
  void duringHoldsWhileOtherEventIsConfirmed() {
    Rule lift = Rule.named("Lift").when(WEIGHT.above(500)).forSamples(2);
    Detector d = Detector.builder()
        .rules(lift)
        .rules(Rule.named("Overload").when(WEIGHT.above(1000).and(during(lift))))
        .listener(rec).build();
    double[] ws = {0, 2000, 2000, 2000, 0};
    for (int i = 0; i < ws.length; i++) d.push(i, Map.of(WEIGHT, ws[i]));
    // at 1 Lift is only Pending, so no Overload yet; at 4 Lift has Ended
    assertEquals(List.of("ended Lift 1..4", "ended Overload 2..4"), ended());
  }

  final Rule maintenance = Rule.named("Maintenance").when(M.above(0));
  final Rule fault = Rule.named("Fault").when(F.above(0));

  Detector.Builder maintenanceThenStart(Condition startCondition) {
    return Detector.builder()
        .rules(maintenance, fault, Rule.named("Start").when(startCondition.and(X.above(0))))
        .listener(rec);
  }

  void push(Detector d, long t, double m, double f, double x) {
    d.push(t, Map.of(M, m, F, f, X, x));
  }

  @Test
  void afterWithinWindow() {
    Detector d = maintenanceThenStart(after(maintenance).within(100)).build();
    push(d, 0, 0, 0, 1);    // no Maintenance yet: no Start
    push(d, 5, 1, 0, 0);
    push(d, 10, 0, 0, 0);   // Maintenance Ended at 10
    push(d, 20, 0, 0, 1);   // Start
    push(d, 30, 0, 0, 0);
    push(d, 200, 0, 0, 1);  // 190 ms after Maintenance: outside window
    assertEquals(List.of("ended Maintenance 5..10", "ended Start 20..30"), ended());
  }

  @Test
  void afterUnlessInterruptingEvent() {
    Detector d = maintenanceThenStart(after(maintenance).unless(fault)).build();
    push(d, 0, 1, 0, 0);
    push(d, 10, 0, 0, 0);
    push(d, 20, 0, 1, 0);   // Fault after Maintenance
    push(d, 30, 0, 0, 1);   // blocked
    push(d, 40, 1, 0, 0);
    push(d, 50, 0, 0, 1);   // new Maintenance Ended at 50, Fault was before it: Start
    d.close();
    assertEquals(List.of("ended Maintenance 0..10 truncatedStart", "ended Fault 20..30",
        "ended Maintenance 40..50", "ended Start 50..50 truncatedEnd"), ended());
  }

  @Test
  void laterRuleSeesEarlierRulesEffectsOnSameSample() {
    Rule a = Rule.named("A").when(A.above(0));
    Rule b = Rule.named("B").when(after(a));
    Detector d = Detector.builder()
        .rules(a, b)
        .listener(rec).build();
    d.push(0, Map.of(A, 1.0));
    d.push(1, Map.of(A, 0.0));
    assertEquals("confirmed B 1", rec.log.get(rec.log.size() - 1));
  }

  @Test
  void selfAndLaterAndMissingReferencesAreRejected() {
    Rule self = Rule.named("A").when(during(Rule.named("A")));
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().rules(self).build());
    Rule b = Rule.named("B").when(X.above(0));
    Rule c = Rule.named("C").when(X.above(0));
    Rule a = Rule.named("A").when(X.above(0)).endWhen(after(b).unless(c));
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().rules(b, a, c).build());
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().rules(c, a).build());
    Detector.builder().rules(b, c, a).build();
  }

  /** A Rule whose Events are only known from History: its Channel never has a value here. */
  static Rule historic(String name) {
    return Rule.named(name).when(Channel.named("unused").above(0));
  }

  @Test
  void historyEnablesAfterFromTheFirstSampleAndIsNotReported() {
    Detector d = Detector.builder()
        .rules(maintenance, Rule.named("Start").when(after(maintenance).within(100)))
        .history(List.of(ended("Maintenance", -80, -70, -50)))
        .listener(rec).build();
    d.push(0, Map.of());
    d.push(60, Map.of()); // 110 ms after Maintenance
    assertEquals(List.of("pending Start 0", "confirmed Start 0", "ended Start 0..60 truncatedStart"),
        rec.log);
  }

  @Test
  void historyEventsMustHaveEnded() {
    Event running = new Event("M", 0, OptionalLong.of(0), OptionalLong.empty(), false, false,
        Map.of());
    assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().history(List.of(running)));
  }

  static Event ended(String name, long start, long confirmed, long end) {
    return new Event(name, start, OptionalLong.of(confirmed), OptionalLong.of(end), false, false,
        Map.of());
  }

  @Test
  void historyMustHaveEndedBeforeTheFirstSample() {
    Rule a = historic("A");
    Detector d = Detector.builder()
        .rules(a, Rule.named("B").when(after(a)))
        .history(List.of(ended("A", 0, 10, 5000))).build();
    assertThrows(IllegalArgumentException.class, () -> d.push(1000, Map.of()));
  }

  @Test
  void historyUnlessUsesConfirmationTime() {
    Rule a = historic("A");
    Rule c = historic("C");
    Rule b = Rule.named("B").when(after(a).unless(c));
    Event aEnded = ended("A", 0, 0, 100);
    Detector blocked = Detector.builder().rules(a, c, b)
        .history(List.of(aEnded, ended("C", 50, 150, 300))).listener(rec).build();
    blocked.push(400, Map.of());
    assertEquals(List.of(), rec.log);

    Detector notBlocked = Detector.builder().rules(a, c, b)
        .history(List.of(aEnded, ended("C", 50, 60, 300))).listener(rec).build();
    notBlocked.push(400, Map.of());
    assertEquals(List.of("pending B 400", "confirmed B 400"), rec.log);
  }
}
