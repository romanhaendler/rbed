package rbed;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static rbed.Conditions.after;
import static rbed.Conditions.channel;
import static rbed.Conditions.during;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Performance tests for the hot path, {@link Detector#push(long, Map)}. Run with
 * {@code mvn test -Pperformance}; they are excluded from the default build because timings depend
 * on the machine.
 *
 * <p>Two kinds of checks:
 *
 * <ul>
 *   <li><b>Allocation checks</b> are deterministic and the real regression guard: in steady state
 *       (no Event starting or ending) pushing a Sample must not allocate, apart from the iterator
 *       over the caller's value map. This is what keeps GC pressure at zero on endless streams and
 *       proves that memory stays bounded however long an Event lasts or however large a
 *       moving-average window is.
 *   <li><b>Throughput checks</b> measure Samples per second for representative Rule sets. Their
 *       floors are roughly 10x below what a current laptop achieves, so they only catch
 *       order-of-magnitude regressions. The measured numbers are printed for comparison.
 * </ul>
 *
 * <p>Inputs are prepared up front (a few HashMaps with pre-boxed values, cycled), so the harness
 * itself allocates nothing per Sample and only the library is measured.
 */
@Tag("performance")
class PerformanceTest {
  private static final int WARM_UP_SAMPLES = 2_000_000;
  private static final int MEASURED_SAMPLES = 5_000_000;
  private static final int RUNS = 5;
  /** The iterator over a HashMap's entries; the only allocation per Sample we accept. */
  private static final long ACCEPTED_BYTES_PER_SAMPLE = 64;

  // ----------------------------------------------------------------------------------------------
  // Scenarios

  /** One Rule, one Channel: the minimum cost of a push. */
  static Detector singleThreshold() {
    return Detector.builder()
        .rule("High", r -> r.when(channel("x").above(50)))
        .build();
  }

  /**
   * A realistic Rule set over 3 Channels that exercises every feature on the hot path: durations,
   * an End Condition, max gap, a Channel-vs-Channel comparison, during, after/unless/within, rate
   * and both kinds of moving average.
   */
  static Detector realisticRuleSet() {
    return Detector.builder()
        .rule("Lift", r -> r
            .when(channel("weight").above(500)).forDuration(2_000)
            .endWhen(channel("weight").below(50)).forDuration(1_000)
            .maxGap(5_000))
        .rule("Overload", r -> r
            .when(channel("weight").above(channel("limit")).and(during("Lift"))).forSamples(3))
        .rule("FastRise", r -> r.when(channel("weight").rate().above(2_000)))
        .rule("Swinging", r -> r
            .when(channel("angle").averageOverSamples(50).above(5)
                .or(channel("angle").averageOverDuration(Duration.ofSeconds(10)).below(-5))))
        .rule("LiftAfterOverload", r -> r
            .when(after("Overload").unless("Lift").within(60_000)
                .and(channel("weight").rising())))
        .build();
  }

  /** 50 Rules over 20 Channels, each with a duration and an End Condition. */
  static Detector manyRulesAndChannels() {
    Detector.Builder b = Detector.builder();
    for (int i = 0; i < 50; i++) {
      String c = "c" + (i % 20);
      b.rule("R" + i, r -> r
          .when(channel(c).above(50)).forSamples(5)
          .endWhen(channel(c).below(10)).forSamples(2));
    }
    return b.build();
  }

  // ----------------------------------------------------------------------------------------------
  // Allocation: deterministic regression guards

  /**
   * Inside one long Event, with moving-average windows full and every Rule idle or Confirmed,
   * pushing must not allocate beyond the map iterator. Covers bounded memory: a window over a
   * minute at 1 kHz keeps only that minute, and an Event's Aggregates do not grow with its length.
   */
  @Test
  void steadyStateSamplesDoNotAllocate() {
    Detector detector = Detector.builder()
        .rule("Long", r -> r
            .when(channel("x").above(0))
            .endWhen(channel("x").below(-1_000)))
        .rule("AvgSamples", r -> r.when(channel("x").averageOverSamples(1_000).above(0)))
        .rule("AvgTime", r -> r.when(channel("x").averageOverDuration(60_000).above(0)))
        .rule("Rate", r -> r.when(channel("x").rate().above(1e9)))
        .build();
    Feed feed = new Feed(8, "x", i -> 1.0 + (i % 7)); // always > 0, never rising 1e9/s

    feed.push(detector, 120_000); // fill every window (2 minutes at 1 kHz), start the Event
    long bytes = allocatedBytes(() -> feed.push(detector, MEASURED_SAMPLES));

    double perSample = (double) bytes / MEASURED_SAMPLES;
    report("steady state allocation", perSample, "bytes/Sample");
    assertTrue(perSample <= ACCEPTED_BYTES_PER_SAMPLE,
        "allocated " + perSample + " bytes per Sample");
  }

  /** The realistic Rule set, too, allocates nothing per Sample while no Event changes state. */
  @Test
  void realisticRuleSetDoesNotAllocateInSteadyState() {
    Detector detector = realisticRuleSet();
    Feed feed = new Feed(4, Map.of("weight", 800.0, "limit", 5_000.0, "angle", 0.0));
    feed.push(detector, 100_000);
    long bytes = allocatedBytes(() -> feed.push(detector, MEASURED_SAMPLES));

    double perSample = (double) bytes / MEASURED_SAMPLES;
    report("realistic steady state allocation", perSample, "bytes/Sample");
    assertTrue(perSample <= ACCEPTED_BYTES_PER_SAMPLE,
        "allocated " + perSample + " bytes per Sample");
  }

  // ----------------------------------------------------------------------------------------------
  // Throughput: order-of-magnitude guards

  @Test
  void throughputSingleThreshold() {
    // crosses the threshold every 100 Samples, so Events start and end all the time
    Feed feed = new Feed(100, "x", i -> i < 50 ? 0.0 : 100.0);
    assertThroughput("single threshold", PerformanceTest::singleThreshold, feed, 5_000_000);
  }

  @Test
  void throughputRealisticRuleSet() {
    Feed feed = new Feed(200, i -> {
      boolean loaded = (i / 50) % 2 == 1;
      return Map.of(
          "weight", loaded ? 1_200.0 + i : 10.0,
          "limit", 5_000.0,
          "angle", (i % 23) - 11.0);
    });
    assertThroughput("realistic rule set", PerformanceTest::realisticRuleSet, feed, 1_000_000);
  }

  /**
   * Stress case: 50 Rules over 20 Channels, every Rule's Event starting and ending every 40
   * Samples. Each Rule aggregates only the one Channel it uses; aggregating all 20 Channels in
   * every running Event made this scenario about 4x slower.
   */
  @Test
  void throughputManyRulesAndChannels() {
    Feed feed = new Feed(40, i -> {
      Map<String, Double> values = new HashMap<>();
      for (int c = 0; c < 20; c++) values.put("c" + c, ((i + c) % 40) < 20 ? 100.0 : 0.0);
      return values;
    });
    assertThroughput("50 rules x 20 channels", PerformanceTest::manyRulesAndChannels, feed,
        150_000);
  }

  // ----------------------------------------------------------------------------------------------
  // Harness

  /**
   * Cycles through a few prepared HashMaps with pre-boxed values, with timestamps 1 ms apart, so
   * the harness allocates nothing per Sample.
   */
  static final class Feed {
    private final Map<String, Double>[] samples;
    private long timestamp;
    private int next;

    Feed(int distinct, String channel, java.util.function.IntToDoubleFunction value) {
      this(distinct, i -> Map.of(channel, value.applyAsDouble(i)));
    }

    Feed(int distinct, Map<String, Double> constant) {
      this(distinct, i -> constant);
    }

    @SuppressWarnings("unchecked")
    Feed(int distinct, java.util.function.IntFunction<Map<String, Double>> sample) {
      samples = new Map[distinct];
      for (int i = 0; i < distinct; i++) samples[i] = new HashMap<>(sample.apply(i));
    }

    void push(Detector detector, int count) {
      for (int i = 0; i < count; i++) {
        detector.push(timestamp++, samples[next]);
        next = next + 1 == samples.length ? 0 : next + 1;
      }
    }
  }

  /** Best of several runs on fresh Detectors after a JIT warm-up. */
  private static void assertThroughput(
      String name, Supplier<Detector> scenario, Feed feed, double floorSamplesPerSecond) {
    feed.push(scenario.get(), WARM_UP_SAMPLES);
    long best = Long.MAX_VALUE;
    for (int run = 0; run < RUNS; run++) {
      Detector detector = scenario.get();
      long start = System.nanoTime();
      feed.push(detector, MEASURED_SAMPLES);
      best = Math.min(best, System.nanoTime() - start);
    }
    double perSecond = MEASURED_SAMPLES / (best / 1e9);
    report(name, perSecond / 1e6, "million Samples/s (" + best / MEASURED_SAMPLES + " ns/Sample)");
    assertTrue(perSecond >= floorSamplesPerSecond,
        name + ": " + (long) perSecond + " Samples/s is below " + (long) floorSamplesPerSecond);
  }

  private static long allocatedBytes(Runnable work) {
    ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    long thread = Thread.currentThread().getId();
    long before = threads.getThreadAllocatedBytes(thread);
    work.run();
    return threads.getThreadAllocatedBytes(thread) - before;
  }

  private static void report(String name, double value, String unit) {
    System.out.printf("[performance] %-36s %10.2f %s%n", name, value, unit);
  }
}
