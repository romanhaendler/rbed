package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;
import static rbed.Conditions.during;
import static rbed.examples.Samples.SECOND;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Detector;
import rbed.Event;
import rbed.Sample;

/**
 * Manufacturing: a machine tool's spindle temperature and load, sampled at 1 Hz without
 * timestamps (sampling-rate mode).
 *
 * <ul>
 *   <li>Overheat: above 80 °C for 30 s, over only once below 70 °C for a minute (hysteresis),
 *       with the peak temperature as attribute.
 *   <li>HighLoad: load above 80 % for 10 s, over below 60 %.
 *   <li>OverheatUnderLoad: both at once ({@code during} combined with {@code and}).
 *   <li>FastHeating: heating faster than 0.5 °C/s for 5 consecutive Samples.
 * </ul>
 *
 * <p>Note: {@code during("Overheat")} stays true until Overheat's End Condition is confirmed
 * (391 s), although its end timestamp is where the End Condition began (331 s). A streaming
 * Detector cannot know earlier that the Event is over.
 */
class MachineToolOverheatExample {

  static double temperature(double s) {
    if (s < 100) return 60;
    if (s < 140) return 60 + (s - 100);  // 1 °C/s
    if (s < 300) return 100;
    if (s < 335) return 100 - (s - 300); // cooling 1 °C/s
    return 65;
  }

  static double load(double s) {
    return s < 100 ? 50 : s < 400 ? 90 : 40;
  }

  @Test
  void detectsOverheatingUnderLoad() {
    Detector detector = Detector.builder()
        .samplingRate(1)
        .rule("HighLoad", r -> r
            .when(channel("load").above(80)).forDuration(10 * SECOND)
            .endWhen(channel("load").below(60)))
        .rule("Overheat", r -> r
            .when(channel("temp").above(80)).forDuration(30 * SECOND)
            .endWhen(channel("temp").below(70)).forDuration(60 * SECOND)
            .attribute("peak", e -> e.aggregates("temp").max()))
        .rule("OverheatUnderLoad", r -> r.when(during("Overheat").and(during("HighLoad"))))
        .rule("FastHeating", r -> r.when(channel("temp").rate().above(0.5)).forSamples(5))
        .build();

    List<Sample> series = Samples.every(1, 0, 600, SECOND,
        s -> Map.of("temp", temperature(s), "load", load(s)));
    List<Event> events = detector.detectValues(series.stream().map(Sample::values).toList());

    assertEquals(List.of(
            "FastHeating 101..141",           // rate is 1 °C/s from 101 s until 140 s
            "Overheat 121..331 {peak=100.0}", // 81 °C at 121 s; below 70 °C from 331 s
            "OverheatUnderLoad 151..391",     // Overheat Confirmed at 151 s, End confirmed 391 s
            "HighLoad 100..400"),
        Samples.describe(events, SECOND));
  }
}
