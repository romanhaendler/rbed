package rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PulseChannelTest {
  final Recorder rec = new Recorder();

  @Test
  void pulseIsUndefinedInSamplesThatDoNotCarryIt() {
    Detector d = Detector.builder()
        .pulse("deploy")
        .rule("Deploying", r -> r.when(channel("deploy").above(0.5)))
        .rule("NotDeploying", r -> r.when(channel("deploy").above(0.5).not()))
        .rule("Held", r -> r.when(channel("normal").above(0.5)))
        .listener(rec).build();
    d.push(0, Map.of("deploy", 1.0, "normal", 1.0));
    d.push(1, Map.of("other", 0.0));
    d.close();
    assertEquals(List.of("ended Deploying 0..1 truncatedStart",
        "ended Held 0..1 truncatedStart truncatedEnd"), rec.endedLines());
  }

  @Test
  void historyFunctionsOnlySeeSamplesCarryingThePulse() {
    Detector d = Detector.builder()
        .pulse("p")
        .rule("Rising", r -> r.when(channel("p").rising()))
        .listener(rec).build();
    d.push(0, Map.of("p", 1.0));
    d.push(1, Map.of());          // undefined, but 1 stays the previous carried value
    d.push(2, Map.of("p", 3.0));  // 3 > 1
    d.push(3, Map.of());
    assertEquals(List.of("ended Rising 2..3 truncatedStart"), rec.endedLines());
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
    assertEquals(new Aggregates(4, 6, 5, 2), rec.ended.get(0).aggregates("p"));
  }
}
