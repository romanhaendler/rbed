package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PulseChannelTest {
  static final Channel DEPLOY = Channel.pulse("deploy");
  static final Channel NORMAL = Channel.named("normal");
  static final Channel OTHER = Channel.named("other");
  static final Channel P = Channel.pulse("p");
  static final Channel X = Channel.named("x");

  final Recorder rec = new Recorder();

  @Test
  void pulseIsAbsentInSamplesThatDoNotCarryIt() {
    Detector d = Detector.builder()
        .rule("Deploying", r -> r.when(DEPLOY.above(0.5)))
        .rule("NotDeploying", r -> r.when(DEPLOY.above(0.5).not()))
        .rule("Held", r -> r.when(NORMAL.above(0.5)))
        .listener(rec).build();
    d.push(0, Map.of(DEPLOY, 1.0, NORMAL, 1.0));
    d.push(1, Map.of(OTHER, 0.0));
    d.close();
    // absent: "deploy above 0.5" is false, so its negation is true
    assertEquals(List.of("ended Deploying 0..1 truncatedStart",
        "ended NotDeploying 1..1 truncatedEnd",
        "ended Held 0..1 truncatedStart truncatedEnd"), rec.endedLines());
  }

  @Test
  void historyFunctionsOnlySeeSamplesCarryingThePulse() {
    Detector d = Detector.builder()
        .rule("Rising", r -> r.when(P.rising()))
        .listener(rec).build();
    d.push(0, Map.of(P, 1.0));
    d.push(1, Map.of());          // absent: not rising; 1 stays the previous carried value
    d.push(2, Map.of(P, 3.0));  // 3 > 1
    d.push(3, Map.of());
    assertEquals(List.of("ended Rising 2..3"), rec.endedLines());
  }

  @Test
  void computingWithAnAbsentPulseIsUndefinedAndAgeKeepsCounting() {
    Detector d = Detector.builder()
        .rule("Sum", r -> r.when(P.plus(1).above(0).or(P.plus(1).above(0).not())))
        .rule("Quiet", r -> r.when(P.age().above(5)))
        .listener(rec).build();
    d.push(0, Map.of(P, 1.0));
    d.push(10, Map.of());
    assertEquals(List.of("pending Sum 0", "confirmed Sum 0", "ended Sum 0..10 truncatedStart",
        "pending Quiet 10", "confirmed Quiet 10"), rec.log);
  }

  @Test
  void aggregatesCountOnlySamplesCarryingThePulse() {
    Detector d = Detector.builder()
        .rule("E", r -> r.when(X.above(0)).aggregate(P))
        .listener(rec).build();
    d.push(0, Map.of(X, 1.0, P, 4.0));
    d.push(1, Map.of(X, 1.0));
    d.push(2, Map.of(X, 1.0, P, 6.0));
    d.push(3, Map.of(X, 0.0));
    assertEquals(Recorder.stats(4, 6, 5, 2), Recorder.stats(rec.ended.get(0).aggregates(P)));
  }

  @Test
  void betweenOnAnAbsentPulseIsFalse() {
    Detector d = Detector.builder()
        .rule("In", r -> r.when(P.between(1, 2)))
        .rule("NotIn", r -> r.when(P.between(1, 2).not()))
        .listener(rec).build();
    d.push(0, Map.of(P, 1.5));
    d.push(1, Map.of());
    assertEquals(List.of("pending In 0", "confirmed In 0", "ended In 0..1 truncatedStart",
        "pending NotIn 1", "confirmed NotIn 1"), rec.log);
  }

  @Test
  void pulseIsAbsentInTheFirstSampleAtTimestampZero() {
    Detector d = Detector.builder()
        .samplingRate(1)
        .rule("NoPulse", r -> r.when(P.above(0).not()))
        .listener(rec).build();
    d.push(Map.of(X, 1.0));
    assertEquals(List.of("pending NoPulse 0", "confirmed NoPulse 0"), rec.log);
  }
}
