package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PulseChannelTest {
  final Recorder rec = new Recorder();

  @Test
  void pulseIsAbsentInSamplesThatDoNotCarryIt() {
    Detector d = Detector.builder()
        .pulse("deploy")
        .rule("Deploying", r -> r.when(channel("deploy").above(0.5)))
        .rule("NotDeploying", r -> r.when(channel("deploy").above(0.5).not()))
        .rule("Held", r -> r.when(channel("normal").above(0.5)))
        .listener(rec).build();
    d.push(0, Map.of("deploy", 1.0, "normal", 1.0));
    d.push(1, Map.of("other", 0.0));
    d.close();
    // absent: "deploy above 0.5" is false, so its negation is true
    assertEquals(List.of("ended Deploying 0..1 truncatedStart",
        "ended NotDeploying 1..1 truncatedEnd",
        "ended Held 0..1 truncatedStart truncatedEnd"), rec.endedLines());
  }

  @Test
  void historyFunctionsOnlySeeSamplesCarryingThePulse() {
    Detector d = Detector.builder()
        .pulse("p")
        .rule("Rising", r -> r.when(channel("p").rising()))
        .listener(rec).build();
    d.push(0, Map.of("p", 1.0));
    d.push(1, Map.of());          // absent: not rising; 1 stays the previous carried value
    d.push(2, Map.of("p", 3.0));  // 3 > 1
    d.push(3, Map.of());
    assertEquals(List.of("ended Rising 2..3"), rec.endedLines());
  }

  @Test
  void computingWithAnAbsentPulseIsUndefinedAndAgeKeepsCounting() {
    Detector d = Detector.builder()
        .pulse("p")
        .rule("Sum", r -> r.when(channel("p").plus(1).above(0).or(channel("p").plus(1).above(0).not())))
        .rule("Quiet", r -> r.when(channel("p").age().above(5)))
        .listener(rec).build();
    d.push(0, Map.of("p", 1.0));
    d.push(10, Map.of());
    assertEquals(List.of("pending Sum 0", "confirmed Sum 0", "ended Sum 0..10 truncatedStart",
        "pending Quiet 10", "confirmed Quiet 10"), rec.log);
  }

  @Test
  void aggregatesCountOnlySamplesCarryingThePulse() {
    Detector d = Detector.builder()
        .pulse("p")
        .rule("E", r -> r.when(channel("x").above(0)).aggregate("p"))
        .listener(rec).build();
    d.push(0, Map.of("x", 1.0, "p", 4.0));
    d.push(1, Map.of("x", 1.0));
    d.push(2, Map.of("x", 1.0, "p", 6.0));
    d.push(3, Map.of("x", 0.0));
    assertEquals(Recorder.stats(4, 6, 5, 2), Recorder.stats(rec.ended.get(0).aggregates("p")));
  }

  @Test
  void betweenOnAnAbsentPulseIsFalse() {
    Detector d = Detector.builder()
        .pulse("p")
        .rule("In", r -> r.when(channel("p").between(1, 2)))
        .rule("NotIn", r -> r.when(channel("p").between(1, 2).not()))
        .listener(rec).build();
    d.push(0, Map.of("p", 1.5));
    d.push(1, Map.of());
    assertEquals(List.of("pending In 0", "confirmed In 0", "ended In 0..1 truncatedStart",
        "pending NotIn 1", "confirmed NotIn 1"), rec.log);
  }

  @Test
  void pulseIsAbsentInTheFirstSampleAtTimestampZero() {
    Detector d = Detector.builder()
        .samplingRate(1)
        .pulse("p")
        .rule("NoPulse", r -> r.when(channel("p").above(0).not()))
        .listener(rec).build();
    d.push(Map.of("x", 1.0));
    assertEquals(List.of("pending NoPulse 0", "confirmed NoPulse 0"), rec.log);
  }
}
