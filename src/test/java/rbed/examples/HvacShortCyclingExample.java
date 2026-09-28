package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.after;
import static rbed.Conditions.count;
import static rbed.examples.Samples.MINUTE;
import static rbed.examples.Samples.SECOND;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Channel;
import rbed.Detector;
import rbed.Event;

/**
 * Building services: an air conditioner's compressor power, every 10 s. Short cycling (starting
 * again shortly after stopping) wears compressors out.
 *
 * <ul>
 *   <li>Run: above 500 W for 30 s, over below 100 W for 30 s. A Run shorter than 5 minutes is
 *       classified as a short cycle by a plain method.
 *   <li>QuickRestart: a Run beginning within 3 minutes after the previous one Ended.
 *   <li>FrequentStarts: more than 2 Runs Confirmed within an hour.
 * </ul>
 */
class HvacShortCyclingExample {
  static final Channel POWER = Channel.named("power");

  static double power(double s) {
    boolean on = (s >= 100 && s < 700) || (s >= 800 && s < 920) || (s >= 1600 && s < 2200);
    return on ? 2_000 : 20;
  }

  /** A Run shorter than 5 minutes. */
  static boolean shortCycle(Event run) {
    return run.end().getAsLong() - run.start() < 5 * MINUTE;
  }

  @Test
  void detectsShortCyclesAndQuickRestarts() {
    Detector detector = Detector.builder()
        .rule("Run", r -> r
            .when(POWER.above(500)).forDuration(30 * SECOND)
            .endWhen(POWER.below(100)).forDuration(30 * SECOND))
        .rule("QuickRestart", r -> r
            .when(after("Run").within(3 * MINUTE).and(POWER.above(500)))
            .forDuration(30 * SECOND)
            .endWhen(POWER.below(100)))
        .rule("FrequentStarts", r -> r.when(count("Run").within(60 * MINUTE).above(2)))
        .build();

    List<Event> events = detector.detect(
        Samples.every(10, 0, 2400, SECOND, s -> Map.of(POWER, power(s))));

    assertEquals(List.of(
            "Run 100..700",
            // QuickRestart Ends at once below 100 W (920 s); Run waits 30 s to confirm its end
            "QuickRestart 800..920",           // 100 s after the first Run Ended
            "Run 800..920",
            "Run 1600..2200",
            // Runs Confirmed at 130, 830 and 1630 s; the window keeps all three until the end
            "FrequentStarts 1630..2390 truncatedEnd"),
        Samples.describe(events, SECOND));
    assertEquals(List.of(false, true, false), events.stream()
        .filter(e -> e.name().equals("Run")).map(HvacShortCyclingExample::shortCycle).toList());
  }
}
