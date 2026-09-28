package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChannelTest {
  final Recorder rec = new Recorder();

  @Test
  void channelsWithTheSameNameAreTheSameChannel() {
    Channel weight = Channel.named("weight");
    Detector d = Detector.builder()
        .rules(Rule.named("Lift").when(weight.above(500)))
        .listener(rec).build();
    Channel fromData = Channel.named("weight"); // e.g. created from a CSV header
    d.push(0, Map.of(fromData, 0.0));
    d.push(1, Map.of(fromData, 600.0, Channel.named("unused"), 1.0));
    d.push(2, Map.of(fromData, 0.0));
    assertEquals(List.of("ended Lift 1..2"), rec.endedLines());
    assertEquals(weight, fromData);
    assertEquals(weight.hashCode(), fromData.hashCode());
  }

  @Test
  void aNameUsedAsPulseAndAsOrdinaryChannelIsRejected() {
    Channel pulse = Channel.pulse("deploy");
    Channel held = Channel.named("deploy");
    Detector.Builder b = Detector.builder()
        .rules(Rule.named("A").when(pulse.above(0)))
        .rules(Rule.named("B").when(Channel.named("x").above(0)).aggregate(held));
    assertThrows(IllegalArgumentException.class, b::build);
  }

  @Test
  void aPulseChannelIsAbsentInSamplesThatDoNotCarryIt() {
    Channel deploy = Channel.pulse("deploy");
    Detector d = Detector.builder()
        .rules(Rule.named("Deploying").when(deploy.above(0.5)))
        .listener(rec).build();
    d.push(0, Map.of(deploy, 1.0));
    d.push(1, Map.of(Channel.named("other"), 0.0));
    assertEquals(List.of("ended Deploying 0..1 truncatedStart"), rec.endedLines());
  }

  @Test
  void aggregatesAreReadByChannel() {
    Channel weight = Channel.named("weight");
    Detector d = Detector.builder()
        .rules(Rule.named("Lift").when(weight.above(500)))
        .listener(rec).build();
    d.push(0, Map.of(weight, 600.0));
    d.push(1, Map.of(weight, 0.0));
    Event lift = rec.ended.get(0);
    assertEquals(600.0, lift.aggregates(Channel.named("weight")).max());
    assertThrows(IllegalArgumentException.class, () -> lift.aggregates(Channel.named("height")));
  }
}
