package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.during;
import static rbed.examples.Samples.SECOND;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Channel;
import rbed.Detector;
import rbed.Event;
import rbed.Rule;
import rbed.EventListener;
import rbed.Sample;

/**
 * Healthcare: heart rate and oxygen saturation from a bedside monitor at 1 Hz, with a 20 s sensor
 * dropout. While no data arrives, the monitor's clock advances the Detector every second, so the
 * dropout is noticed while it lasts, not only once data resumes.
 *
 * <ul>
 *   <li>Tachycardia: heart rate above 100 for 30 s, over below 95 for 30 s. A dropout longer than
 *       10 s interrupts it: the Event Ends truncated, and the one after the dropout starts
 *       truncated because nobody knows what happened in between.
 *   <li>Desaturation: SpO2 below 90 % for 10 s, over at 92 % or more.
 *   <li>HypoxicTachycardia: both at the same time.
 *   <li>SensorLost: no heart rate for more than 5 s.
 * </ul>
 */
class VitalSignsExample {
  static final Channel HR = Channel.named("hr");
  static final Channel SPO2 = Channel.named("spo2");

  @Test
  void detectsCombinedAlarmsAndHandlesSensorDropout() {
    List<Event> ended = new ArrayList<>();
    Rule tachycardia = Rule.named("Tachycardia")
        .when(HR.above(100)).forDuration(30 * SECOND)
        .endWhen(HR.below(95)).forDuration(30 * SECOND)
        .maxGap(10 * SECOND);
    Rule desaturation = Rule.named("Desaturation")
        .when(SPO2.below(90)).forDuration(10 * SECOND)
        .endWhen(SPO2.below(92).not());
    Rule hypoxicTachycardia = Rule.named("HypoxicTachycardia")
        .when(during(tachycardia).and(during(desaturation)));
    Rule sensorLost = Rule.named("SensorLost").when(HR.age().above(5 * SECOND));
    Detector detector = Detector.builder()
        .rules(tachycardia, desaturation, hypoxicTachycardia, sensorLost)
        .listener(new EventListener() {
          @Override public void onEnded(Event e) {
            ended.add(e);
          }
        })
        .build();

    for (Sample sample : Samples.every(1, 0, 600, SECOND, s -> Map.of(
        HR, s < 60 ? 80.0 : s < 300 ? 120.0 : 85.0,
        SPO2, s < 120 ? 97.0 : s < 200 ? 86.0 : 96.0))) {
      boolean dropout = sample.timestamp() >= 150 * SECOND && sample.timestamp() < 170 * SECOND;
      if (dropout) detector.advanceTo(sample.timestamp());
      else detector.push(sample.timestamp(), sample.values());
    }
    detector.close();

    assertEquals(List.of(
            // stale at 160 s, 11 s after the last Sample; valid until the advance at 159 s
            "Tachycardia 60..159 truncatedEnd",
            "HypoxicTachycardia 130..160",        // Tachycardia interrupted
            "SensorLost 155..170",                // 6 s without heart rate until data resumes
            "Desaturation 120..200",
            "Tachycardia 170..300 truncatedStart"),
        Samples.describe(ended, SECOND));
  }
}
