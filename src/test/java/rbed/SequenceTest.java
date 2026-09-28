package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static rbed.Conditions.after;
import static rbed.Conditions.channel;
import static rbed.Conditions.during;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class SequenceTest {
  final Recorder rec = new Recorder();

  List<String> ended() {
    return rec.endedLines();
  }

  @Test
  void duringHoldsWhileOtherEventIsConfirmed() {
    Detector d = Detector.builder()
        .rule("Lift", r -> r.when(channel("weight").above(500)).forSamples(2))
        .rule("Overload", r -> r.when(channel("weight").above(1000).and(during("Lift"))))
        .listener(rec).build();
    double[] ws = {0, 2000, 2000, 2000, 0};
    for (int i = 0; i < ws.length; i++) d.push(i, Map.of("weight", ws[i]));
    // at 1 Lift is only Pending, so no Overload yet; at 4 Lift has Ended
    assertEquals(List.of("ended Lift 1..4", "ended Overload 2..4"), ended());
  }

  Detector.Builder maintenanceThenStart(Condition startCondition) {
    return Detector.builder()
        .rule("Maintenance", r -> r.when(channel("m").above(0)))
        .rule("Fault", r -> r.when(channel("f").above(0)))
        .rule("Start", r -> r.when(startCondition.and(channel("x").above(0))))
        .listener(rec);
  }

  void push(Detector d, long t, double m, double f, double x) {
    d.push(t, Map.of("m", m, "f", f, "x", x));
  }

  @Test
  void afterWithinWindow() {
    Detector d = maintenanceThenStart(after("Maintenance").within(100)).build();
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
    Detector d = maintenanceThenStart(after("Maintenance").unless("Fault")).build();
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
    Detector d = Detector.builder()
        .rule("A", r -> r.when(channel("a").above(0)))
        .rule("B", r -> r.when(after("A")))
        .listener(rec).build();
    d.push(0, Map.of("a", 1.0));
    d.push(1, Map.of("a", 0.0));
    assertEquals("confirmed B 1", rec.log.get(rec.log.size() - 1));
  }

  @Test
  void selfAndForwardReferencesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> Detector.builder()
        .rule("A", r -> r.when(during("A"))).build());
    assertThrows(IllegalArgumentException.class, () -> Detector.builder()
        .rule("A", r -> r.when(channel("x").above(0)).endWhen(after("B").unless("C")))
        .rule("C", r -> r.when(channel("x").above(0))).build());
    // names no Rule produces are fine: they can only come from History
    Detector.builder().rule("A", r -> r.when(after("External"))).build();
  }

  @Test
  void historyEnablesAfterFromTheFirstSampleAndIsNotReported() {
    Event maintenance = ended("Maintenance", -80, -70, -50);
    Detector d = Detector.builder()
        .rule("Start", r -> r.when(after("Maintenance").within(100)))
        .history(List.of(maintenance))
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
    Detector d = Detector.builder()
        .rule("B", r -> r.when(after("A")))
        .history(List.of(ended("A", 0, 10, 5000))).build();
    assertThrows(IllegalArgumentException.class, () -> d.push(1000, Map.of()));
  }

  @Test
  void historyUnlessUsesConfirmationTime() {
    Event a = ended("A", 0, 0, 100);
    Condition bAfterA = after("A").unless("C");
    Detector blocked = Detector.builder().rule("B", r -> r.when(bAfterA))
        .history(List.of(a, ended("C", 50, 150, 300))).listener(rec).build();
    blocked.push(400, Map.of());
    assertEquals(List.of(), rec.log);

    Detector notBlocked = Detector.builder().rule("B", r -> r.when(bAfterA))
        .history(List.of(a, ended("C", 50, 60, 300))).listener(rec).build();
    notBlocked.push(400, Map.of());
    assertEquals(List.of("pending B 400", "confirmed B 400"), rec.log);
  }
}
