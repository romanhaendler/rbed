package io.github.romanhaendler.rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static io.github.romanhaendler.rbed.Conditions.after;
import static io.github.romanhaendler.rbed.Conditions.count;
import static io.github.romanhaendler.rbed.examples.Samples.MINUTE;
import static io.github.romanhaendler.rbed.examples.Samples.SECOND;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.Rule;

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
    Rule run = Rule.named("Run")
        .when(POWER.above(500)).forDuration(30 * SECOND)
        .endWhen(POWER.below(100)).forDuration(30 * SECOND);
    Rule quickRestart = Rule.named("QuickRestart")
        .when(after(run).within(3 * MINUTE).and(POWER.above(500)))
        .forDuration(30 * SECOND)
        .endWhen(POWER.below(100));
    Rule frequentStarts = Rule.named("FrequentStarts")
        .when(count(run).within(60 * MINUTE).above(2));
    Detector detector = Detector.builder()
        .rules(run, quickRestart, frequentStarts)
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
        .filter(e -> e.is(run)).map(HvacShortCyclingExample::shortCycle).toList());
  }
}
