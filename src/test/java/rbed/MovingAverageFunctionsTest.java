package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MovingAverageFunctionsTest {
  static final Channel X = Channel.named("x");

  final Recorder rec = new Recorder();

  /** One Sample per second with the given values of x. */
  List<String> run(Detector.Builder builder, double... xs) {
    Detector d = builder.listener(rec).build();
    for (int i = 0; i < xs.length; i++) d.push(i * 1000L, Map.of(X, xs[i]));
    d.close();
    return rec.endedLines();
  }

  // x:              0, 10,  0, 10, 20, 30
  // average of 2:   -,  5,  5,  5, 15, 25
  // its rate (/s):  -,  -,  0,  0, 10, 10
  static final double[] NOISY = {0, 10, 0, 10, 20, 30};

  @Test
  void rateOfAMovingAverageDoesNotFlicker() {
    assertEquals(List.of("ended Raw 1000..2000 truncatedStart", "ended Raw 3000..5000 truncatedEnd",
            "ended Smooth 4000..5000 truncatedEnd"),
        run(Detector.builder()
            .rules(Rule.named("Raw").when(X.rate().above(5)))
            .rules(Rule.named("Smooth").when(X.averageOverSamples(2).rate().above(5))),
            NOISY));
  }

  @Test
  void risingAndFallingOfAMovingAverage() {
    assertEquals(List.of("ended Up 4000..5000 truncatedEnd"), run(Detector.builder()
            .rules(Rule.named("Up").when(X.averageOverSamples(2).rising())),
        NOISY));
  }

  @Test
  void averageOfAnAverage() {
    // average of 2 of the average above: -, -, 5, 5, 10, 20
    assertEquals(List.of("ended E 4000..5000 truncatedEnd"), run(Detector.builder()
            .rules(Rule.named("E").when(
                X.averageOverSamples(2).averageOverDuration(1000).above(9))),
        NOISY));
  }

  @Test
  void movingAverageInEndConditionFillsFromTheFirstSample() {
    // Confirmed at 2 s; average of 2 is falling first at 5 s ((200 + 5) / 2 < 200)
    assertEquals(List.of("ended E 0..5000 truncatedStart"), run(Detector.builder()
            .rules(Rule.named("E")
                .when(X.above(100)).forSamples(3)
                .endWhen(X.averageOverSamples(2).falling())),
        200, 200, 200, 200, 200, 5, 5));
  }
}
