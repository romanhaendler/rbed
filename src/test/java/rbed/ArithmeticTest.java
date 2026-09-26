package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArithmeticTest {
  final Recorder rec = new Recorder();

  /** Returns the timestamps at which a Rule on {@code c} is Confirmed, one Sample per map. */
  List<String> confirmations(Condition c, List<Map<String, Double>> samples) {
    Detector d = Detector.builder().rule("E", r -> r.when(c)).listener(rec).build();
    for (int i = 0; i < samples.size(); i++) d.push(i, samples.get(i));
    return rec.log.stream().filter(line -> line.startsWith("confirmed")).toList();
  }

  @Test
  void differenceOfTwoChannels() {
    // HVAC: supply air more than 5 degrees warmer than return air
    assertEquals(List.of("confirmed E 1"), confirmations(
        channel("supply").minus(channel("return")).above(5),
        List.of(Map.of("supply", 20.0, "return", 18.0), Map.of("supply", 26.0))));
  }

  @Test
  void scalingAndOffsets() {
    assertEquals(List.of("confirmed E 1"), confirmations(
        channel("x").times(2).plus(1).above(10),   // 2x + 1 > 10  <=>  x > 4.5
        List.of(Map.of("x", 4.0), Map.of("x", 5.0))));
    rec.log.clear();
    assertEquals(List.of("confirmed E 1"), confirmations(
        channel("x").minus(1).dividedBy(2).below(channel("y").plus(channel("x")).times(0.1)),
        List.of(Map.of("x", 9.0, "y", 1.0), Map.of("x", 1.0))));
  }

  @Test
  void absoluteValue() {
    assertEquals(List.of("confirmed E 1"), confirmations(
        channel("vibration").abs().above(3),
        List.of(Map.of("vibration", 2.0), Map.of("vibration", -5.0))));
  }

  @Test
  void divisionByZeroIsUndefinedNeverTrue() {
    Condition ratio = channel("x").dividedBy(channel("y")).above(1);
    assertEquals(List.of(), confirmations(ratio.or(ratio.not()),
        List.of(Map.of("x", 1.0, "y", 0.0), Map.of("x", -1.0))));
  }

  @Test
  void undefinedOperandMakesTheResultUndefined() {
    Condition sum = channel("x").plus(channel("missing")).above(0);
    assertEquals(List.of(), confirmations(sum.or(sum.not()), List.of(Map.of("x", 1.0))));
  }
}
