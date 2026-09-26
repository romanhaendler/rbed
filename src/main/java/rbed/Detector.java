package rbed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Receives Samples one at a time and evaluates Rules against them, reporting Event lifecycles to
 * an {@link EventListener}. A Detector is stateful, single-use and not thread-safe: use one per
 * data stream.
 */
public final class Detector implements AutoCloseable {
  final EventListener listener;
  private final List<Rule> rules;
  private final Map<String, Double> values = new HashMap<>();
  private final Map<String, ChannelState> channels = new HashMap<>();
  private final Map<MovingAverage, MovingAverage.Window> windows = new IdentityHashMap<>();
  private final Map<String, Long> lastEnd = new HashMap<>();
  private final Map<String, Long> lastConfirmed = new HashMap<>();
  private final Set<String> running = new HashSet<>();
  private long samples;
  private long lastTimestamp;
  private long now;
  private boolean closed;
  private final double samplingRate;
  private List<Event> collected;

  private Detector(Builder b) {
    listener = b.listener;
    samplingRate = b.samplingRate;
    lastEnd.putAll(b.historyEnd);
    lastConfirmed.putAll(b.historyConfirmed);
    rules = new ArrayList<>();
    b.rules.forEach((name, rb) -> {
      rules.add(new Rule(name, rb));
      for (Condition c : new Condition[] {rb.when, rb.endWhen}) {
        if (c != null) c.visit(node -> {
          if (node instanceof MovingAverage ma && !windows.containsKey(ma)) {
            MovingAverage.Window w = ma.newWindow();
            windows.put(ma, w);
            channel(ma.channel.channel).windows.add(w);
          }
        });
      }
    });
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Processes one Sample.
   *
   * @param timestamp milliseconds; must be strictly greater than the previous Sample's
   * @param channelValues values of any subset of Channels; must be finite
   */
  public void push(long timestamp, Map<String, Double> channelValues) {
    if (samplingRate > 0) {
      throw new IllegalStateException("Detector has a sampling rate: push values only");
    }
    accept(timestamp, channelValues);
  }

  /**
   * Processes one Sample of a regularly sampled series; its timestamp is derived from the
   * sampling rate as {@code round(index * 1000 / hz)}.
   *
   * @throws IllegalStateException if no sampling rate was configured
   */
  public void push(Map<String, Double> channelValues) {
    if (samplingRate <= 0) {
      throw new IllegalStateException("Detector has no sampling rate: push timestamps too");
    }
    accept(Math.round(samples * 1000 / samplingRate), channelValues);
  }

  /**
   * Runs a whole recorded series through this Detector and closes it. The listener is still
   * called.
   *
   * @return all Ended Events in the order they Ended
   */
  public List<Event> detect(Iterable<Sample> series) {
    return batch(() -> series.forEach(s -> push(s.timestamp(), s.values())));
  }

  /** Like {@link #detect}, for a regularly sampled series without timestamps. */
  public List<Event> detectValues(Iterable<Map<String, Double>> series) {
    return batch(() -> series.forEach(this::push));
  }

  private List<Event> batch(Runnable pushAll) {
    if (samples > 0 || closed) throw new IllegalStateException("Detector has already been used");
    collected = new ArrayList<>();
    pushAll.run();
    close();
    return collected;
  }

  private void accept(long timestamp, Map<String, Double> channelValues) {
    if (closed) throw new IllegalStateException("Detector is closed");
    if (samples > 0 && timestamp <= lastTimestamp) {
      throw new IllegalArgumentException(
          "Timestamp " + timestamp + " is not after previous " + lastTimestamp);
    }
    channelValues.forEach((channel, v) -> {
      Objects.requireNonNull(channel, "channel name");
      if (v == null || !Double.isFinite(v)) {
        throw new IllegalArgumentException("Value of channel '" + channel + "' is " + v);
      }
    });
    values.putAll(channelValues);
    channelValues.forEach((channel, v) -> channel(channel).add(timestamp, v));
    now = timestamp;
    Long previous = samples == 0 ? null : lastTimestamp;
    for (Rule rule : rules) rule.step(this, timestamp, previous);
    lastTimestamp = timestamp;
    samples++;
  }

  /** Ends running Events as truncated and makes this Detector reject further Samples. */
  @Override
  public void close() {
    if (closed) return;
    closed = true;
    if (samples > 0) for (Rule rule : rules) rule.close(this, lastTimestamp);
  }

  /** Current (held) values of all Channels that have received one. */
  Map<String, Double> values() {
    return values;
  }

  /** Timestamp of the Sample being processed. */
  long now() {
    return now;
  }

  void confirmed(String event, long timestamp) {
    running.add(event);
    lastConfirmed.put(event, timestamp);
  }

  void ended(Event event) {
    running.remove(event.name());
    lastEnd.put(event.name(), event.end().getAsLong());
    if (collected != null) collected.add(event);
    listener.onEnded(event);
  }

  boolean isRunning(String event) {
    return running.contains(event);
  }

  Long lastEnd(String event) {
    return lastEnd.get(event);
  }

  Long lastConfirmed(String event) {
    return lastConfirmed.get(event);
  }

  ChannelState channel(String name) {
    return channels.computeIfAbsent(name, n -> new ChannelState());
  }

  MovingAverage.Window window(MovingAverage ma) {
    return windows.get(ma);
  }

  /** Last two values of one Channel and the moving-average windows fed by it. */
  static final class ChannelState {
    double value = Double.NaN;
    double previous = Double.NaN;
    long time;
    long previousTime;
    final List<MovingAverage.Window> windows = new ArrayList<>();

    void add(long t, double v) {
      previous = value;
      previousTime = time;
      value = v;
      time = t;
      for (MovingAverage.Window w : windows) w.add(t, v);
    }
  }

  /** Builds a {@link Detector}. */
  public static final class Builder {
    private final Map<String, RuleBuilder> rules = new java.util.LinkedHashMap<>();
    private EventListener listener = new EventListener() {};
    private final Map<String, Long> historyEnd = new HashMap<>();
    private final Map<String, Long> historyConfirmed = new HashMap<>();
    private double samplingRate;

    private Builder() {}

    /** Adds a Rule; its name becomes the name of the Events it produces. */
    public Builder rule(String name, UnaryOperator<RuleBuilder> config) {
      if (name == null || name.isEmpty()) throw new IllegalArgumentException("Rule name is empty");
      if (rules.containsKey(name)) throw new IllegalArgumentException("Duplicate rule " + name);
      RuleBuilder rb = config.apply(new RuleBuilder());
      if (rb.when == null) throw new IllegalArgumentException("Rule " + name + " has no when()");
      rules.put(name, rb);
      return this;
    }

    public Builder listener(EventListener listener) {
      this.listener = Objects.requireNonNull(listener);
      return this;
    }

    /**
     * Declares a regularly sampled series of {@code hz} Samples per second, so Samples are pushed
     * without timestamps.
     */
    public Builder samplingRate(double hz) {
      if (!(hz > 0) || Double.isInfinite(hz)) {
        throw new IllegalArgumentException("Sampling rate must be positive: " + hz);
      }
      samplingRate = hz;
      return this;
    }

    /**
     * Supplies Events known from before this Detector started (e.g. stored before a restart), so
     * that {@link Conditions#after} and {@link After#unless} see them. They are not reported to
     * the listener. Their confirmation time is taken to be their start.
     */
    public Builder history(Collection<Event> events) {
      for (Event e : events) {
        if (e.end().isEmpty()) {
          throw new IllegalArgumentException("History Event " + e.name() + " has not Ended");
        }
        historyEnd.merge(e.name(), e.end().getAsLong(), Math::max);
        historyConfirmed.merge(e.name(), e.start(), Math::max);
      }
      return this;
    }

    /**
     * @throws IllegalArgumentException if a Rule references itself or a Rule declared after it
     */
    public Detector build() {
      List<String> names = new ArrayList<>(rules.keySet());
      for (int i = 0; i < names.size(); i++) {
        String rule = names.get(i);
        List<String> later = names.subList(i, names.size());
        RuleBuilder rb = rules.get(rule);
        for (Condition c : new Condition[] {rb.when, rb.endWhen}) {
          if (c == null) continue;
          c.visit(node -> {
            List<String> refs = node instanceof During du ? List.of(du.name)
                : node instanceof After af ? af.referencedEvents() : List.of();
            for (String ref : refs) {
              if (later.contains(ref)) {
                throw new IllegalArgumentException("Rule " + rule + " references " + ref
                    + ", which is not declared before it");
              }
            }
          });
        }
      }
      return new Detector(this);
    }
  }
}
