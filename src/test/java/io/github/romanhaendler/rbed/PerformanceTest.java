package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.romanhaendler.rbed.Conditions.after;
import static io.github.romanhaendler.rbed.Conditions.count;
import static io.github.romanhaendler.rbed.Conditions.during;
import static io.github.romanhaendler.rbed.Conditions.latest;
import static io.github.romanhaendler.rbed.Conditions.totalDuration;

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
 *       (no Event starting or ending) pushing a Sample must not allocate at all. The Detector
 *       walks the caller's HashMap with {@code forEach}, so this does not depend on the JIT's
 *       escape analysis. This keeps GC pressure at zero on endless streams and proves that memory
 *       stays bounded however long an Event lasts or however large a moving-average window is.
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
  static final Channel ANGLE = Channel.named("angle");
  static final Channel LIMIT = Channel.named("limit");
  static final Channel WEIGHT = Channel.named("weight");
  static final Channel X = Channel.named("x");

  private static final int WARM_UP_SAMPLES = 2_000_000;
  private static final int MEASURED_SAMPLES = 5_000_000;
  private static final int RUNS = 5;
  /** Nothing per Sample; the tolerance only absorbs one-off allocations of the JVM itself. */
  private static final double ACCEPTED_BYTES_PER_SAMPLE = 1;

  // ----------------------------------------------------------------------------------------------
  // Scenarios

  /** One Rule, one Channel: the minimum cost of a push. */
  static Detector singleThreshold() {
    return Detector.builder()
        .rules(Rule.named("High").when(X.above(50)))
        .build();
  }

  /**
   * A realistic Rule set over 3 Channels that exercises every feature on the hot path: durations,
   * an End Condition, max gap, a Channel-vs-Channel comparison, during, after/unless/within, rate,
   * both kinds of moving average, a smoothed rate, arithmetic, count, totalDuration, latest and
   * age.
   */
  static Detector realisticRuleSet() {
    Rule lift = Rule.named("Lift")
        .when(WEIGHT.above(500)).forDuration(2_000)
        .endWhen(WEIGHT.below(50)).forDuration(1_000)
        .maxGap(5_000);
    Rule overload = Rule.named("Overload")
        .when(WEIGHT.above(LIMIT).and(during(lift))).forSamples(3);
    return Detector.builder()
        .rules(lift, overload)
        .rules(Rule.named("FastRise").when(WEIGHT.rate().above(2_000)))
        .rules(Rule.named("Swinging")
            .when(ANGLE.averageOverSamples(50).above(5)
                .or(ANGLE.averageOverDuration(Duration.ofSeconds(10)).below(-5))))
        .rules(Rule.named("LiftAfterOverload")
            .when(after(overload).unless(lift).within(60_000)
                .and(WEIGHT.rising())))
        .rules(Rule.named("NearLimit")
            .when(LIMIT.minus(WEIGHT).abs().below(100)))
        .rules(Rule.named("SmoothRise")
            .when(WEIGHT.averageOverSamples(10).rate().above(100)))
        .rules(Rule.named("FrequentLifts").when(count(lift).within(60_000).above(3)))
        .rules(Rule.named("LongLifting").when(totalDuration(lift).above(3_600_000)))
        .rules(Rule.named("PartialSetDown")
            .when(during(lift)
                .and(WEIGHT.below(latest(lift).max(WEIGHT).times(0.5)))))
        .rules(Rule.named("AngleSensorSilent").when(ANGLE.age().above(5_000)))
        .build();
  }

  /** 50 Rules over 20 Channels, each with a duration and an End Condition. */
  static Detector manyRulesAndChannels() {
    Detector.Builder b = Detector.builder();
    for (int i = 0; i < 50; i++) {
      Channel c = Channel.named("c" + (i % 20));
      b.rules(Rule.named("R" + i)
          .when(c.above(50)).forSamples(5)
          .endWhen(c.below(10)).forSamples(2));
    }
    return b.build();
  }

  // ----------------------------------------------------------------------------------------------
  // Allocation: deterministic regression guards

  /**
   * Inside one long Event, with moving-average windows full and every Rule idle or Confirmed,
   * pushing must not allocate beyond the map iterator, watched Expressions included. Covers
   * bounded memory: a window over a minute at 1 kHz keeps only that minute, and an Event's
   * Aggregates do not grow with its length.
   */
  @Test
  void steadyStateSamplesDoNotAllocate() {
    Detector detector = Detector.builder()
        .rules(Rule.named("Long")
            .when(X.above(0))
            .endWhen(X.below(-1_000)))
        .rules(Rule.named("AvgSamples").when(X.averageOverSamples(1_000).above(0)))
        .rules(Rule.named("AvgTime").when(X.averageOverDuration(60_000).above(0)))
        .rules(Rule.named("Rate").when(X.rate().above(1e9)))
        .watch(X, X.averageOverSamples(1_000), X.averageOverDuration(60_000), X.rate(), X.age(),
            X.minus(X.averageOverSamples(10)).abs()) // watching adds no work to a push
        .build();
    Feed feed = new Feed(8, X, i -> 1.0 + (i % 7)); // always > 0, never rising 1e9/s

    feed.push(detector, 120_000); // fill every window (2 minutes at 1 kHz), start the Event
    long bytes = allocatedBytes(() -> feed.push(detector, MEASURED_SAMPLES));

    double perSample = (double) bytes / MEASURED_SAMPLES;
    report("steady state allocation", perSample, "bytes/Sample");
    assertTrue(perSample <= ACCEPTED_BYTES_PER_SAMPLE,
        "allocated " + perSample + " bytes per Sample");
  }

  /** A duration window held at the Retention Limit drops an entry every Sample, still for free. */
  @Test
  void droppingAtTheRetentionLimitDoesNotAllocate() {
    Detector detector = Detector.builder().retentionLimit(1_000)
        .rules(Rule.named("AvgTime").when(X.averageOverDuration(60_000).above(0)))
        .build();
    Feed feed = new Feed(8, X, i -> 1.0 + (i % 7));

    feed.push(detector, 120_000); // far beyond the limit: the window is full and dropping
    long dropsBefore = detector.droppedValues();
    long bytes = allocatedBytes(() -> feed.push(detector, MEASURED_SAMPLES));

    assertTrue(detector.droppedValues() - dropsBefore >= MEASURED_SAMPLES, "no drops measured");
    double perSample = (double) bytes / MEASURED_SAMPLES;
    report("dropping allocation", perSample, "bytes/Sample");
    assertTrue(perSample <= ACCEPTED_BYTES_PER_SAMPLE,
        "allocated " + perSample + " bytes per Sample");
  }

  /**
   * Counting Events in a window costs the same however many Events the window holds: 1,000 vs.
   * 100,000 Confirmed Events inside it (an Event every 4 ms) within a small factor.
   */
  @Test
  void countWithinWindowDoesNotSlowDownWithEventsInWindow() {
    double few = countThroughput(4_000);
    double many = countThroughput(400_000);
    report("count, 1,000 Events in window", few / 1e6, "million Samples/s");
    report("count, 100,000 Events in window", many / 1e6, "million Samples/s");
    assertTrue(many * 3 >= few, "100,000 Events: " + (long) many + " vs. 1,000: " + (long) few);
  }

  private static double countThroughput(long windowMillis) {
    Rule event = Rule.named("E").when(X.above(50)).endWhen(X.below(10));
    Feed feed = new Feed(2, X, i -> i == 0 ? 100.0 : 0.0);
    Detector warm = Detector.builder()
        .rules(event, Rule.named("C").when(count(event).within(windowMillis).above(1e9))).build();
    feed.push(warm, WARM_UP_SAMPLES);
    long best = Long.MAX_VALUE;
    for (int run = 0; run < RUNS; run++) {
      Detector detector = Detector.builder()
          .rules(event, Rule.named("C").when(count(event).within(windowMillis).above(1e9)))
          .build();
      feed.push(detector, (int) (2 * windowMillis)); // fill the window, not measured
      assertTrue(detector.retainedValues() >= windowMillis / 4, "window not filled: " + detector.retainedValues());
      long start = System.nanoTime();
      feed.push(detector, MEASURED_SAMPLES);
      best = Math.min(best, System.nanoTime() - start);
    }
    return MEASURED_SAMPLES / (best / 1e9);
  }

  /** The realistic Rule set, too, allocates nothing per Sample while no Event changes state. */
  @Test
  void realisticRuleSetDoesNotAllocateInSteadyState() {
    Detector detector = realisticRuleSet();
    Feed feed = new Feed(4, Map.of(WEIGHT, 800.0, LIMIT, 5_000.0, ANGLE, 0.0));
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
    Feed feed = new Feed(100, X, i -> i < 50 ? 0.0 : 100.0);
    assertThroughput("single threshold", PerformanceTest::singleThreshold, feed, 5_000_000);
  }

  @Test
  void throughputRealisticRuleSet() {
    Feed feed = new Feed(200, i -> {
      boolean loaded = (i / 50) % 2 == 1;
      return Map.of(
          WEIGHT, loaded ? 1_200.0 + i : 10.0,
          LIMIT, 5_000.0,
          ANGLE, (i % 23) - 11.0);
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
      Map<Channel, Double> values = new HashMap<>();
      for (int c = 0; c < 20; c++) values.put(Channel.named("c" + c), ((i + c) % 40) < 20 ? 100.0 : 0.0);
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
    private final Map<Channel, Double>[] samples;
    private long timestamp;
    private int next;

    Feed(int distinct, Channel channel, java.util.function.IntToDoubleFunction value) {
      this(distinct, i -> Map.of(channel, value.applyAsDouble(i)));
    }

    Feed(int distinct, Map<Channel, Double> constant) {
      this(distinct, i -> constant);
    }

    @SuppressWarnings("unchecked")
    Feed(int distinct, java.util.function.IntFunction<Map<Channel, Double>> sample) {
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
