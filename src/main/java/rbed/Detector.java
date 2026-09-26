package rbed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Receives Samples one at a time and evaluates Rules against them, reporting Event lifecycles to
 * an {@link EventListener}. A Detector is stateful, single-use and not thread-safe: use one per
 * data stream.
 */
public final class Detector implements AutoCloseable {
  /** Timestamps have millisecond resolution, so faster sampling would repeat timestamps. */
  static final double MAX_SAMPLING_RATE = 1000;

  final EventListener listener;
  private final double samplingRate;
  private final Map<String, ChannelState> channelsByName = new HashMap<>();
  private final List<ChannelState> channels = new ArrayList<>();
  private final Map<String, EventState> events = new HashMap<>();
  private final boolean hasHistory;
  private final long latestHistoryEnd;
  private final Rule[] rules;
  private long now;
  private long samples;
  private boolean closed;
  private List<Event> collected;

  /** While binding Rules: the Rule being bound and the names it must not reference. */
  private String bindingRule;
  private Set<String> notDeclaredBefore;

  private Detector(Builder b) {
    listener = b.listener;
    samplingRate = b.samplingRate;
    long latest = Long.MIN_VALUE;
    for (Event e : b.history) {
      event(e.name()).remember(e);
      latest = Math.max(latest, e.end().getAsLong());
    }
    hasHistory = !b.history.isEmpty();
    latestHistoryEnd = latest;

    notDeclaredBefore = new HashSet<>(b.rules.keySet());
    rules = new Rule[b.rules.size()];
    int i = 0;
    for (Map.Entry<String, RuleBuilder> rule : b.rules.entrySet()) {
      bindingRule = rule.getKey();
      rules[i++] = new Rule(this, rule.getKey(), rule.getValue());
      notDeclaredBefore.remove(rule.getKey());
    }
    notDeclaredBefore = null;
    bindingRule = null;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Processes one Sample.
   *
   * @param timestamp milliseconds; must be strictly greater than the previous Sample's and than
   *     the end of every History Event
   * @param values values of any subset of Channels; must be finite
   * @throws IllegalStateException if a sampling rate was configured, or after {@link #close}
   */
  public void push(long timestamp, Map<String, Double> values) {
    if (samplingRate > 0) {
      throw new IllegalStateException("Detector has a sampling rate: push values only");
    }
    accept(timestamp, values);
  }

  /**
   * Processes one Sample of a regularly sampled series; its timestamp is derived from the
   * sampling rate as {@code round(index * 1000 / hz)}.
   *
   * @throws IllegalStateException if no sampling rate was configured, or after {@link #close}
   */
  public void push(Map<String, Double> values) {
    if (samplingRate == 0) {
      throw new IllegalStateException("Detector has no sampling rate: push timestamps too");
    }
    accept(Math.round(samples * 1000 / samplingRate), values);
  }

  /**
   * Runs a whole recorded series through this unused Detector and closes it. The listener is
   * still called.
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

  /** Ends running Events as truncated and makes this Detector reject further Samples. */
  @Override
  public void close() {
    if (closed) return;
    closed = true;
    if (samples > 0) for (Rule rule : rules) rule.close(now);
  }

  private List<Event> batch(Runnable pushAll) {
    if (samples > 0 || closed) throw new IllegalStateException("Detector has already been used");
    collected = new ArrayList<>();
    pushAll.run();
    close();
    return collected;
  }

  private void accept(long timestamp, Map<String, Double> values) {
    if (closed) throw new IllegalStateException("Detector is closed");
    boolean first = samples == 0;
    if (first ? hasHistory && timestamp <= latestHistoryEnd : timestamp <= now) {
      throw new IllegalArgumentException("Timestamp " + timestamp + " is not after "
          + (first ? "the end of the History, " + latestHistoryEnd : "the previous one, " + now));
    }
    // validate everything first, so a rejected Sample leaves no trace
    for (Map.Entry<String, Double> value : values.entrySet()) {
      Double v = value.getValue();
      if (value.getKey() == null || v == null || !Double.isFinite(v)) {
        throw new IllegalArgumentException("Invalid value " + v + " of channel " + value.getKey());
      }
    }
    for (Map.Entry<String, Double> value : values.entrySet()) {
      channel(value.getKey()).add(timestamp, value.getValue());
    }
    long previous = now;
    now = timestamp;
    for (Rule rule : rules) rule.step(timestamp, previous, first);
    samples++;
  }

  /** Timestamp of the latest Sample. */
  long now() {
    return now;
  }

  void ended(Event event) {
    if (collected != null) collected.add(event);
    listener.onEnded(event);
  }

  /** All Channels, in the order they became known; index-aligned with {@link Accumulator}. */
  List<ChannelState> channels() {
    return channels;
  }

  ChannelState channel(String name) {
    ChannelState state = channelsByName.get(name);
    if (state == null) {
      state = new ChannelState(name);
      channelsByName.put(name, state);
      channels.add(state);
    }
    return state;
  }

  EventState event(String name) {
    EventState state = events.get(name);
    if (state == null) {
      state = new EventState();
      events.put(name, state);
    }
    return state;
  }

  /**
   * The state of Events a Condition refers to, while binding a Rule.
   *
   * @throws IllegalArgumentException if the Rule refers to itself or a Rule declared after it
   */
  EventState referencedEvent(String name) {
    if (notDeclaredBefore != null && notDeclaredBefore.contains(name)) {
      throw new IllegalArgumentException(
          "Rule " + bindingRule + " references " + name + ", which is not declared before it");
    }
    return event(name);
  }

  /** Builds a {@link Detector}. */
  public static final class Builder {
    private final Map<String, RuleBuilder> rules = new LinkedHashMap<>();
    private final List<Event> history = new ArrayList<>();
    private EventListener listener = new EventListener() {};
    private double samplingRate;

    private Builder() {}

    /** Adds a Rule; its name becomes the name of the Events it produces. */
    public Builder rule(String name, UnaryOperator<RuleBuilder> config) {
      if (name == null || name.isEmpty()) throw new IllegalArgumentException("Rule name is empty");
      if (rules.containsKey(name)) throw new IllegalArgumentException("Duplicate rule " + name);
      RuleBuilder rule = config.apply(new RuleBuilder());
      if (rule == null || rule.when == null) {
        throw new IllegalArgumentException("Rule " + name + " has no when()");
      }
      rules.put(name, rule);
      return this;
    }

    public Builder listener(EventListener listener) {
      this.listener = Objects.requireNonNull(listener, "listener");
      return this;
    }

    /**
     * Declares a regularly sampled series of {@code hz} Samples per second (at most 1000, the
     * millisecond resolution of timestamps), so Samples are pushed without timestamps.
     */
    public Builder samplingRate(double hz) {
      if (!(hz > 0 && hz <= MAX_SAMPLING_RATE)) {
        throw new IllegalArgumentException("Sampling rate must be in (0, 1000] Hz: " + hz);
      }
      samplingRate = hz;
      return this;
    }

    /**
     * Supplies Events known from before this Detector started (e.g. stored before a restart), so
     * that {@link Conditions#after} and {@link After#unless} see them. They must be Confirmed and
     * Ended before the first Sample, and are not reported to the listener.
     */
    public Builder history(Collection<Event> events) {
      for (Event e : events) {
        if (e.confirmed().isEmpty() || e.end().isEmpty()) {
          throw new IllegalArgumentException("History Event " + e.name() + " has not Ended");
        }
        history.add(e);
      }
      return this;
    }

    /**
     * @throws IllegalArgumentException if a Rule references itself or a Rule declared after it
     */
    public Detector build() {
      return new Detector(this);
    }
  }
}
