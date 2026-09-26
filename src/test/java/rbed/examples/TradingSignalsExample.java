package rbed.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static rbed.Conditions.channel;
import static rbed.examples.Series.DAY;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rbed.Detector;
import rbed.Event;

/**
 * Finance: daily closing prices, falling for 300 days and then rising. Golden and death crosses
 * of the 50-day and 200-day simple moving averages.
 *
 * <p>The death cross is Truncated at its start: when both averages first exist (day 199), the
 * 50-day one is already below, so nobody knows when it crossed.
 *
 * <p>The expected crossing day is computed independently by brute force below.
 */
class TradingSignalsExample {

  static double price(double day) {
    return day < 300 ? 200 - day / 3 : 100 + (day - 300) / 2;
  }

  static double sma(int lastDay, int n) {
    double sum = 0;
    for (int d = lastDay - n + 1; d <= lastDay; d++) sum += price(d);
    return sum / n;
  }

  @Test
  void detectsMovingAverageCrosses() {
    Detector detector = Detector.builder()
        .rule("DeathCross", r -> r.when(
            channel("price").averageOverSamples(50).below(channel("price").averageOverSamples(200))))
        .rule("GoldenCross", r -> r.when(
            channel("price").averageOverSamples(50).above(channel("price").averageOverSamples(200))))
        .build();

    List<Event> events = detector.detect(
        Series.every(1, 0, 600, DAY, day -> Map.of("price", price(day))));

    int golden = 199;
    while (!(sma(golden, 50) > sma(golden, 200))) golden++;
    assertEquals(List.of(
            "DeathCross 199.." + golden + " truncatedStart",
            "GoldenCross " + golden + "..599 truncatedEnd"),
        Series.describe(events, DAY));
  }
}
