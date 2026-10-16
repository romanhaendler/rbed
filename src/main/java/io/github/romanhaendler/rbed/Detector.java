package io.github.romanhaendler.rbed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

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
  private final Map<Channel, ChannelState> channels = new HashMap<>();
  private final Map<String, EventState> events = new HashMap<>();
  private final boolean hasHistory;
  private final long latestHistoryEnd;
  private final RuleState[] rules;
  private final ChannelState[] pulses;
  /** The latest moment processed: a Sample or a time advance. */
  private long now;
  private boolean started;
  private long lastSample;
  private long samples;
  private boolean closed;
  /** Entries held by moving-average windows and count timestamps; see {@link #retainedValues}. */
  long retained;
  private final int retentionLimit;
  private long dropped;
  private List<Event> collected;
  /** Timestamp of the Sample being applied by {@link #applyValue}. */
  private long sampleTimestamp;
  /**
   * Callbacks for {@link Map#forEach}, created once: iterating a HashMap this way needs no
   * iterator, so pushing a Sample allocates nothing regardless of what the JIT inlines.
   */
  private final BiConsumer<Channel, Double> validateValue = Detector::validate;
  private final BiConsumer<Channel, Double> applyValue =
      (channel, value) -> {
        ChannelState state = channels.get(channel);
        if (state != null) state.update(sampleTimestamp, value); // unused Channels are ignored
      };

  /**
   * While binding Rules: the Rule being bound, the names of all Rules and of those bound before
   * it, and the Channels its Conditions refer to.
   */
  String bindingRule;
  private Set<String> added;
  private Set<String> addedBefore;
  private Set<ChannelState> channelLookups;

  private Detector(Builder b) {
    listener = b.listener;
    samplingRate = b.samplingRate;
    retentionLimit = b.retentionLimit;
    added = b.rules.keySet();
    addedBefore = new HashSet<>();
    rules = new RuleState[b.rules.size()];
    int i = 0;
    for (Rule rule : b.rules.values()) {
      bindingRule = rule.name();
      rules[i++] = new RuleState(this, rule);
      addedBefore.add(rule.name());
    }
    added = null;
    addedBefore = null;
    bindingRule = null;
    pulses = channels.values().stream()
        .filter(state -> state.channel.pulse)
        .toArray(ChannelState[]::new);

    hasHistory = !b.history.isEmpty();
    latestHistoryEnd = remember(b.history); // after binding, so that Event counts see it too
  }

  /** Applies History in order of confirmation; returns its latest end. */
  private long remember(List<Event> history) {
    List<Event> ordered = new ArrayList<>(history);
    ordered.sort(Comparator.comparingLong(e -> e.confirmed().getAsLong()));
    long latestEnd = Long.MIN_VALUE;
    for (Event e : ordered) {
      if (!events.containsKey(e.name())) {
        throw new IllegalArgumentException("History Event " + e.name() + " matches no Rule");
      }
      event(e.name()).remember(e);
      latestEnd = Math.max(latestEnd, e.end().getAsLong());
    }
    return latestEnd;
  }

  /**
   * The number of entries this Detector currently holds: one per Channel a Rule uses, one per
   * value in each moving-average window and one per timestamp kept for
   * {@code count(...).within(...)}. A plain counter read, cheap to poll between pushes.
   */
  public long retainedValues() {
    return channels.size() + retained;
  }

  /**
   * The number of entries dropped because a window was full (see
   * {@link Builder#retentionLimit}) since this Detector was built. Entries that left a window by
   * time are not counted.
   */
  public long droppedValues() {
    return dropped;
  }

  int retentionLimit() {
    return retentionLimit;
  }

  /** Counts an entry dropped from the full window of {@code rule}; warns on the first one. */
  void dropped(String rule) {
    if (dropped++ == 0) {
      System.getLogger("io.github.romanhaendler.rbed").log(System.Logger.Level.WARNING,
          "Rule " + rule + ": a window reached the Retention Limit of " + retentionLimit
              + " entries; its oldest entries are dropped, so results deviate from the Rule."
              + " Raise the limit with Detector.builder().retentionLimit(...).");
    }
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
  public void push(long timestamp, Map<Channel, Double> values) {
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
  public void push(Map<Channel, Double> values) {
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
  public List<Event> detectValues(Iterable<Map<Channel, Double>> series) {
    return batch(() -> series.forEach(this::push));
  }

  /** Ends running Events as truncated and makes this Detector reject further Samples. */
  @Override
  public void close() {
    if (closed) return;
    closed = true;
    if (started) for (RuleState rule : rules) rule.close();
  }

  private List<Event> batch(Runnable pushAll) {
    if (started || closed) throw new IllegalStateException("Detector has already been used");
    collected = new ArrayList<>();
    pushAll.run();
    close();
    return collected;
  }

  /**
   * Tells this Detector that time has passed up to {@code timestamp} without new Samples. Rules
   * are evaluated at that moment with every Channel holding its value (Pulse Channels are
   * absent): durations can be reached, max gaps noticed, and {@code age()} grows. An advance is
   * not a Sample, so it does not count for {@code forSamples} or Sample-based Aggregates.
   *
   * @param timestamp milliseconds; must be strictly greater than the previous Sample or advance
   * @throws IllegalStateException if a sampling rate was configured, or after {@link #close}
   */
  public void advanceTo(long timestamp) {
    if (samplingRate > 0) {
      throw new IllegalStateException("Detector has a sampling rate: time advances with Samples");
    }
    moveTo(timestamp);
    for (ChannelState pulse : pulses) pulse.pulseAbsent();
    evaluate(timestamp, false);
  }

  private void accept(long timestamp, Map<Channel, Double> values) {
    values.forEach(validateValue); // first, so a rejected Sample leaves no trace
    moveTo(timestamp);
    for (ChannelState pulse : pulses) pulse.carried = false;
    sampleTimestamp = timestamp;
    values.forEach(applyValue);
    for (ChannelState pulse : pulses) if (!pulse.carried) pulse.pulseAbsent();
    evaluate(timestamp, true);
    lastSample = timestamp;
    samples++;
  }

  /** Evaluates every Rule, in declaration order, at the current moment. */
  private void evaluate(long timestamp, boolean isSample) {
    for (RuleState rule : rules) rule.step(timestamp, isSample);
  }

  private static void validate(Channel channel, Double value) {
    if (channel == null || value == null || !Double.isFinite(value)) {
      throw new IllegalArgumentException("Invalid value " + value + " of channel " + channel);
    }
  }

  private void moveTo(long timestamp) {
    if (closed) throw new IllegalStateException("Detector is closed");
    if (started ? timestamp <= now : hasHistory && timestamp <= latestHistoryEnd) {
      throw new IllegalArgumentException("Timestamp " + timestamp + " is not after " + (started
          ? "the previous one, " + now
          : "the end of the History, " + latestHistoryEnd));
    }
    now = timestamp;
    started = true;
  }

  /** Timestamp of the latest Sample or time advance. */
  long now() {
    return now;
  }

  boolean hasSamples() {
    return samples > 0;
  }

  /** Timestamp of the latest Sample; while Rules evaluate a Sample, the one before it. */
  long lastSample() {
    return lastSample;
  }

  void ended(Event event) {
    if (collected != null) collected.add(event);
    listener.onEnded(event);
  }

  /** A Channel's state, created when first referenced by a Rule. */
  private ChannelState state(Channel channel) {
    ChannelState state = channels.get(channel);
    if (state == null) {
      state = new ChannelState(channel);
      channels.put(channel, state);
    }
    return state;
  }

  /**
   * The state of a Channel a Rule refers to, while binding it.
   *
   * @throws IllegalArgumentException if another reference disagrees on whether it is a pulse
   */
  ChannelState channel(Channel channel) {
    ChannelState state = state(channel);
    if (state.channel.pulse != channel.pulse) {
      throw new IllegalArgumentException(
          "Channel " + channel + " is used both as a Pulse Channel and as an ordinary Channel");
    }
    return state;
  }

  /** A Channel that a Condition being bound refers to; recorded for the Rule's Aggregates. */
  ChannelState referencedChannel(Channel channel) {
    ChannelState state = channel(channel);
    if (channelLookups != null) channelLookups.add(state);
    return state;
  }

  /** Starts recording which Channels the Conditions being bound refer to. */
  void recordChannelLookups() {
    channelLookups = new LinkedHashSet<>();
  }

  /** The Channels looked up since {@link #recordChannelLookups()}; stops recording. */
  Set<ChannelState> recordedChannelLookups() {
    Set<ChannelState> recorded = channelLookups;
    channelLookups = null;
    return recorded;
  }

  EventState event(String name) {
    EventState state = events.get(name);
    if (state == null) {
      state = new EventState(this);
      events.put(name, state);
    }
    return state;
  }

  /**
   * The state of Events a Condition refers to, while binding a Rule.
   *
   * @throws IllegalArgumentException if the Rule refers to itself, to a Rule added after it or to
   *     a Rule not added to this Detector
   */
  EventState referencedEvent(Rule rule) {
    String name = rule.name();
    if (!addedBefore.contains(name)) {
      throw new IllegalArgumentException("Rule " + bindingRule + " references " + name
          + (added.contains(name) ? ", which is not added before it" : ", which is not added"));
    }
    return event(name);
  }

  /** Builds a {@link Detector}. */
  public static final class Builder {
    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private final List<Event> history = new ArrayList<>();
    private EventListener listener = new EventListener() {};
    private double samplingRate;
    private int retentionLimit = 1_000_000;

    private Builder() {}

    /**
     * Adds Rules, evaluated in the order they are added. A Rule may only refer to Rules added
     * before it.
     */
    public Builder rules(Rule... rules) {
      for (Rule rule : rules) {
        String name = rule.name();
        if (rule.when == null) {
          throw new IllegalArgumentException("Rule " + name + " has no when()");
        }
        if (this.rules.containsKey(name)) {
          throw new IllegalArgumentException("Duplicate rule " + name);
        }
        this.rules.put(name, rule);
      }
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
     * Sets the Retention Limit: the maximum number of entries a single {@code averageOverDuration}
     * window keeps (default 1,000,000). When a full window gets a new value, its oldest entry is
     * dropped, and the Detector logs one warning through {@link System.Logger}. See
     * {@link Detector#droppedValues}.
     *
     * @throws IllegalArgumentException if {@code n} is below 1
     */
    public Builder retentionLimit(int n) {
      if (n < 1) throw new IllegalArgumentException("Retention Limit must be >= 1: " + n);
      retentionLimit = n;
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
     * Builds the Detector and binds every Rule's Conditions to it.
     *
     * @throws IllegalArgumentException if a Rule references itself, a Rule added after it or a
     *     Rule not added, or if History contains an Event of no added Rule. A Rule without
     *     {@code when} and duplicate Rule names are already rejected by {@link #rules}.
     */
    public Detector build() {
      return new Detector(this);
    }
  }
}
