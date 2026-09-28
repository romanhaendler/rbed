package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static rbed.Conditions.during;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class RuleTest {
  static final Channel X = Channel.named("x");

  final Recorder rec = new Recorder();

  @Test
  void configuringARuleLeavesTheOriginalUnchanged() {
    Rule quick = Rule.named("High").when(X.above(0));
    Rule slow = quick.forSamples(2);
    Recorder quickRec = new Recorder();
    Detector.builder().rules(quick).listener(quickRec).build().push(0, Map.of(X, 1.0));
    Detector.builder().rules(slow).listener(rec).build().push(0, Map.of(X, 1.0));
    assertEquals(List.of("pending High 0", "confirmed High 0"), quickRec.log);
    assertEquals(List.of("pending High 0"), rec.log);
  }

  @Test
  void eventsTellWhichRuleProducedThem() {
    Rule high = Rule.named("High").when(X.above(0));
    Rule low = Rule.named("Low").when(X.below(0));
    Detector d = Detector.builder().rules(high, low).listener(rec).build();
    d.push(0, Map.of(X, 1.0));
    d.push(1, Map.of(X, -1.0));
    Event e = rec.ended.get(0);
    assertTrue(e.is(high));
    assertFalse(e.is(low));
    assertEquals("High", e.name());
  }

  @Test
  void aReferenceToARuleNotAddedIsRejected() {
    Rule a = Rule.named("A").when(X.above(0));
    Rule b = Rule.named("B").when(during(a));
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().rules(b).build());
  }

  @Test
  void aReferenceToALaterRuleIsRejected() {
    Rule a = Rule.named("A").when(X.above(0));
    Rule b = Rule.named("B").when(during(a));
    assertThrows(IllegalArgumentException.class, () -> Detector.builder().rules(b, a).build());
  }

  @Test
  void duplicateRuleNamesAreRejected() {
    Rule a = Rule.named("A").when(X.above(0));
    assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().rules(a, Rule.named("A").when(X.below(0))).build());
  }

  @Test
  void aRuleWithoutWhenIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().rules(Rule.named("A")).build());
  }

  @Test
  void historyOfAnUnknownRuleIsRejected() {
    Event old = new Event("Renamed", 0, OptionalLong.of(0), OptionalLong.of(1), false, false,
        Map.of());
    Rule a = Rule.named("A").when(X.above(0));
    assertThrows(IllegalArgumentException.class,
        () -> Detector.builder().rules(a).history(List.of(old)).build());
  }
}
