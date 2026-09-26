package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.after;
import static rbed.Conditions.channel;
import static rbed.Conditions.count;
import static rbed.examples.Series.MINUTE;
import static rbed.examples.Series.SECOND;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Detector;
import rbed.Event;

/**
 * Building services: an air conditioner's compressor power, every 10 s. Short cycling (starting
 * again shortly after stopping) wears compressors out.
 *
 * <ul>
 *   <li>Run: above 500 W for 30 s, over below 100 W for 30 s. A Run shorter than 5 minutes is
 *       classified as a short cycle via an attribute.
 *   <li>QuickRestart: a Run beginning within 3 minutes after the previous one Ended.
 *   <li>FrequentStarts: more than 2 Runs Confirmed within an hour.
 * </ul>
 */
class HvacShortCyclingExample {

  static double power(double s) {
    boolean on = (s >= 100 && s < 700) || (s >= 800 && s < 920) || (s >= 1600 && s < 2200);
    return on ? 2_000 : 20;
  }

  @Test
  void detectsShortCyclesAndQuickRestarts() {
    Detector detector = Detector.builder()
        .rule("Run", r -> r
            .when(channel("power").above(500)).forDuration(30 * SECOND)
            .endWhen(channel("power").below(100)).forDuration(30 * SECOND)
            .attribute("shortCycle", e -> e.end().getAsLong() - e.start() < 5 * MINUTE))
        .rule("QuickRestart", r -> r
            .when(after("Run").within(3 * MINUTE).and(channel("power").above(500)))
            .forDuration(30 * SECOND)
            .endWhen(channel("power").below(100)))
        .rule("FrequentStarts", r -> r.when(count("Run").within(60 * MINUTE).above(2)))
        .build();

    List<Event> events = detector.detect(
        Series.every(10, 0, 2400, SECOND, s -> Map.of("power", power(s))));

    assertEquals(List.of(
            "Run 100..700 {shortCycle=false}",
            // QuickRestart Ends at once below 100 W (920 s); Run waits 30 s to confirm its end
            "QuickRestart 800..920",           // 100 s after the first Run Ended
            "Run 800..920 {shortCycle=true}",
            "Run 1600..2200 {shortCycle=false}",
            // Runs Confirmed at 130, 830 and 1630 s; the window keeps all three until the end
            "FrequentStarts 1630..2390 truncatedEnd"),
        Series.describe(events, SECOND));
  }
}
