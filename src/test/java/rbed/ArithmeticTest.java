package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArithmeticTest {
  static final Channel MISSING = Channel.named("missing");
  static final Channel RETURN = Channel.named("return");
  static final Channel SUPPLY = Channel.named("supply");
  static final Channel VIBRATION = Channel.named("vibration");
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");

  final Recorder rec = new Recorder();

  /** Returns the timestamps at which a Rule on {@code c} is Confirmed, one Sample per map. */
  List<String> confirmations(Condition c, List<Map<Channel, Double>> samples) {
    Detector d = Detector.builder().rules(Rule.named("E").when(c)).listener(rec).build();
    for (int i = 0; i < samples.size(); i++) d.push(i, samples.get(i));
    return rec.log.stream().filter(line -> line.startsWith("confirmed")).toList();
  }

  @Test
  void differenceOfTwoChannels() {
    // HVAC: supply air more than 5 degrees warmer than return air
    assertEquals(List.of("confirmed E 1"), confirmations(
        SUPPLY.minus(RETURN).above(5),
        List.of(Map.of(SUPPLY, 20.0, RETURN, 18.0), Map.of(SUPPLY, 26.0))));
  }

  @Test
  void scalingAndOffsets() {
    assertEquals(List.of("confirmed E 1"), confirmations(
        X.times(2).plus(1).above(10),   // 2x + 1 > 10  <=>  x > 4.5
        List.of(Map.of(X, 4.0), Map.of(X, 5.0))));
    rec.log.clear();
    assertEquals(List.of("confirmed E 1"), confirmations(
        X.minus(1).dividedBy(2).below(Y.plus(X).times(0.1)),
        List.of(Map.of(X, 9.0, Y, 1.0), Map.of(X, 1.0))));
  }

  @Test
  void absoluteValue() {
    assertEquals(List.of("confirmed E 1"), confirmations(
        VIBRATION.abs().above(3),
        List.of(Map.of(VIBRATION, 2.0), Map.of(VIBRATION, -5.0))));
  }

  @Test
  void divisionByZeroIsUndefinedNeverTrue() {
    Condition ratio = X.dividedBy(Y).above(1);
    assertEquals(List.of(), confirmations(ratio.or(ratio.not()),
        List.of(Map.of(X, 1.0, Y, 0.0), Map.of(X, -1.0))));
  }

  @Test
  void undefinedOperandMakesTheResultUndefined() {
    Condition sum = X.plus(MISSING).above(0);
    assertEquals(List.of(), confirmations(sum.or(sum.not()), List.of(Map.of(X, 1.0))));
  }
}
