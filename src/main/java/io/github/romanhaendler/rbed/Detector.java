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
import java.util.OptionalDouble;
import java.util.OptionalLong;
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
  private boolean hasHistory;
  private long latestHistoryEnd;
  private final RuleState[] rules;
  private final ChannelState[] pulses;
  private final ChannelState[] tracks;
  /** Events whose confirmation times are kept for {@code count(...).within(...)}. */
  private final EventState[] counted;
  /** Some Rule has a finite maxGap, so the previous Sample matters. */
  private final boolean gaps;
  /** Watched Expressions, bound once; a Channel matches by name, others by identity. */
  private final Map<Expression, Expression.Bound> watched = new HashMap<>();
  /** The latest moment processed: a Sample or a time advance. */
  private long now;
  private boolean started;
  private long lastSample;
  /**
   * Runs of Rules that are over, as (start, end] pairs, oldest end first: a replay must not start
   * inside one, or it would split the Event (see {@link #replayPoint}). Entries that end before
   * the Replay Point are dropped; if the rest fills the array, neighbours are merged into one
   * larger pair, which only makes the Replay Point earlier.
   */
  private static final int MAX_RUNS = 64;
  private final long[] runStarts = new long[MAX_RUNS];
  private final long[] runEnds = new long[MAX_RUNS];
  private int runCount;
  private long samples;
  private boolean closed;
  /** A push, advance or close is running, so a listener callback may be on the stack. */
  private boolean processing;
  /** Descriptions of the Rules, their Channels and the watched Expressions, for the Snapshot fingerprint. */
  private final List<String> ruleDescriptions = new ArrayList<>();
  private final List<String> watchedDescriptions = new ArrayList<>();
  /** Entries held by moving-average windows and count timestamps; see {@link #retainedValues}. */
  private long retained;
  private final int retentionLimit;
  private long horizonMillis;
  private int horizonSamples;
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
   * While binding: what is being bound for messages ("Rule X" or "Watched Expression y"), the
   * names of all Rules and of those bound before it, and the Channels a Rule's Conditions refer to.
   */
  String binding;
  private Set<String> added;
  private Set<String> addedBefore;
  private Set<ChannelState> channelLookups;
  private Map<ValueTrack, Integer> trackReads;
  private Set<RuleState> latestReads;

  private Detector(Builder b) {
    listener = b.listener;
    samplingRate = b.samplingRate;
    retentionLimit = b.retentionLimit;
    added = b.rules.keySet();
    addedBefore = new HashSet<>();
    rules = new RuleState[b.rules.size()];
    int i = 0;
    for (Rule rule : b.rules.values()) {
      binding = "Rule " + rule.name();
      rules[i++] = new RuleState(this, rule);
      addedBefore.add(rule.name());
    }
    for (Expression expression : b.watched) { // after all Rules, so it may refer to any of them
      binding = "Watched Expression " + expression;
      watched.put(expression, expression.bind(this));
    }
    added = null;
    addedBefore = null;
    binding = null;
    pulses = channels.values().stream()
        .filter(state -> state.channel.pulse)
        .toArray(ChannelState[]::new);

    tracks = channels.values().toArray(ChannelState[]::new);
    counted = events.values().stream().filter(EventState::keepsConfirmations)
        .toArray(EventState[]::new);
    boolean anyGap = false;
    for (RuleState rule : rules) anyGap |= rule.hasMaxGap();
    gaps = anyGap;

    for (Rule rule : b.rules.values()) ruleDescriptions.add(rule.toString());
    for (ChannelState state : sortedChannels()) {
      ruleDescriptions.add((state.channel.pulse ? "pulse channel " : "channel ") + state.channel.name());
    }
    for (Expression expression : b.watched) watchedDescriptions.add(expression.toString());

    if (b.restore != null) {
      if (!b.history.isEmpty()) {
        throw new IllegalArgumentException(
            "restore(...) and history(...) cannot be combined: a Snapshot already contains the"
                + " History that was known when it was taken");
      }
      Snapshots.restore(this, b.restore);
      return;
    }
    hasHistory = !b.history.isEmpty();
    latestHistoryEnd = remember(b.history); // after binding, so that Event counts see it too
  }

  private ChannelState[] sortedChannels() {
    return channels.values().stream()
        .sorted(Comparator.comparing(state -> state.channel.name()))
        .toArray(ChannelState[]::new);
  }

  /**
   * Returns this Detector's complete state at the latest moment processed, as a byte array that
   * {@link Builder#restore} turns back into a Detector.
   *
   * <p>The guarantee: a Detector restored from a Snapshot is indistinguishable from the one the
   * Snapshot was taken from. For every input that follows, the callbacks (kind, order and the
   * complete Event, every Aggregate bit for bit), the values read through {@link #value}, {@link
   * #retainedValues()}, {@link #droppedValues()}, the warnings logged and the exceptions thrown
   * are identical. The Snapshot also works after {@link #close} (it restores a closed Detector),
   * and before the first Sample. It does not contain the listener; the restored Detector reports
   * to the Builder's listener. Taking a Snapshot does not change this Detector. Its size is
   * bounded by the Retention Limit.
   *
   * <p>The array starts with a magic header and a format version, followed by a fingerprint of
   * the Rules and options; {@link Builder#build()} rejects a Snapshot that does not match.
   *
   * @throws IllegalStateException if called from within an {@link EventListener} callback, where
   *     a moment is only half processed
   */
  public byte[] snapshot() {
    if (processing) {
      throw new IllegalStateException(
          "snapshot() cannot be called from a listener callback: the moment is only half"
              + " processed. Take it after push, advanceTo or close returns.");
    }
    return Snapshots.write(this);
  }

  List<String> ruleDescriptions() {
    return ruleDescriptions;
  }

  List<String> watchedDescriptions() {
    return watchedDescriptions;
  }

  double samplingRate() {
    return samplingRate;
  }

  void saveState(StateWriter w) {
    w.bool(started);
    w.int64(now);
    w.bool(closed);
    w.int64(lastSample);
    w.int64(samples);
    w.int64(retained);
    w.int64(dropped);
    w.bool(hasHistory);
    w.int64(latestHistoryEnd);
    w.int32(runCount);
    for (int i = 0; i < runCount; i++) {
      w.int64(runStarts[i]);
      w.int64(runEnds[i]);
    }
    ChannelState[] sorted = sortedChannels();
    w.int32(sorted.length);
    for (ChannelState state : sorted) state.save(w);
    for (RuleState rule : rules) rule.save(w);
  }

  void loadState(StateReader r) throws java.io.IOException {
    started = r.bool();
    now = r.int64();
    closed = r.bool();
    lastSample = r.int64();
    samples = r.int64();
    retained = r.int64();
    dropped = r.int64();
    hasHistory = r.bool();
    latestHistoryEnd = r.int64();
    int runs = r.length();
    if (runs > MAX_RUNS) throw new java.io.IOException("runs");
    for (int i = 0; i < runs; i++) {
      runStarts[i] = r.int64();
      runEnds[i] = r.int64();
    }
    runCount = runs;
    ChannelState[] sorted = sortedChannels();
    r.expect(sorted.length);
    for (ChannelState state : sorted) state.load(r);
    for (RuleState rule : rules) rule.load(r);
  }

  /**
   * The value of a watched Expression at the latest moment processed (Sample or time advance),
   * also after {@link #close}. Empty while the value is undefined, for example a Channel before
   * its first value, a moving average during its warm-up, a Pulse Channel absent in that moment
   * or {@code latest(...)} before the first such Event. Expressions about Events are defined from
   * the start: before the first Sample, {@code count(...)} and {@code totalDuration(...)} are 0,
   * or what the History gives. Reading only looks the value up.
   *
   * <p>From an EventListener callback, it reads the moment being processed. Rules are evaluated
   * in the order they were added, so for a Rule added later than the Rule whose callback runs,
   * {@code count}, {@code totalDuration} and {@code latest} show its Events as of the previous
   * moment: an Event of it Confirmed or Ended in this moment is not reflected yet, nor is this
   * moment in its Aggregates; a running Event's duration counts up to this moment.
   *
   * @throws IllegalArgumentException if the Expression was not registered with
   *     {@link Builder#watch}; a Channel matches by name, every other Expression by identity
   * @throws NullPointerException if {@code expression} is null
   */
  public OptionalDouble value(Expression expression) {
    Expression.Bound bound = watched.get(Objects.requireNonNull(expression, "expression"));
    if (bound == null) {
      throw new IllegalArgumentException("Expression " + expression
          + " is not watched. Register it with Detector.builder().watch(...).");
    }
    double v = bound.value();
    return v == v ? OptionalDouble.of(v) : OptionalDouble.empty();
  }

  /** Called while binding by every windowed construct; unbounded ({@code Long.MAX_VALUE}) counts for nothing. */
  void windowMillis(long millis) {
    if (millis != Long.MAX_VALUE) horizonMillis = Math.max(horizonMillis, millis);
  }

  void windowSamples(int n) {
    horizonSamples = Math.max(horizonSamples, n);
  }

  /**
   * How far back this Detector's configuration makes it look: the longest time window and the
   * largest Sample count used by its Rules and watched Expressions, computed when it was built.
   * This is a configuration bound, not a guarantee. An Event in progress, a Rule waiting for its
   * {@code when} Condition to stop holding, and {@code latest(...)} can depend on Samples further
   * back; the Replay Point is the exact answer to how far back a replay must start.
   */
  public MemoryHorizon memoryHorizon() {
    return new MemoryHorizon(horizonMillis, horizonSamples);
  }

  /**
   * The earliest timestamp from which replaying this Detector's input into a fresh Detector
   * (same Rules and options), with the Events that Ended before that timestamp supplied as
   * {@link Builder#history History}, reproduces this Detector exactly: the replay then continues
   * with the same Events, callbacks, values and exceptions as this Detector. Empty before the
   * first Sample, and with a sampling rate (a fresh Detector cannot continue the Sample index).
   * Read it after each moment, store it with the stream position, and the stream has to be kept
   * back to the oldest Replay Point still needed.
   *
   * <p>Replay all moments (Samples, time advances, and {@link #close} if it was called) with a
   * timestamp from the Replay Point on, in order, with the listener's side effects suppressed up
   * to the position where the replay catches up with this Detector. The History is every Event
   * that Ended before the Replay Point, including those supplied to this Detector as History;
   * Events that Ended later are produced again by the replay.
   *
   * <p>The Replay Point reaches back to what the state of this Detector depends on, value by
   * value: the Events in progress, including the End Condition's duration; a Rule that has not
   * seen its {@code when} Condition stop holding since its last Event ended on an End Condition,
   * so a fresh Rule would start another Event; the moment {@code when} was last seen false, which
   * decides whether the next Event is Truncated at its start; the oldest Sample in each
   * moving-average window; the latest and previous values read by {@code rate()}, {@code age()},
   * {@code rising()}, {@code falling()} and the previous Sample behind {@code maxGap}; the
   * Channel values the Rules read; and the start of the latest Event of every Rule whose
   * Aggregates are read through {@code latest(...)}. It is not inside the span of an Event that
   * another Rule reads or that ended on an End Condition, so such an Event is never split. On a
   * steady input it keeps up with the stop, within what the constructs in use need. Reading it
   * takes time proportional to the number of Channels, moving averages, Rules and finished runs
   * kept, not to the history, and allocates only the result.
   *
   * <p>Preconditions: the replay gets the same input, Rules and options, and its listener's side
   * effects are suppressed up to the stop; the Detector has
   * not dropped entries because of the Retention Limit ({@link #droppedValues} is 0), which would
   * make windows depend on the dropped entries; and {@code droppedValues()}, a count since the
   * Detector was built, and the warning logged with the first drop are not reproduced by a replay.
   * A moving average depends only on the values in its window, so a replay reproduces it bit for
   * bit.
   */
  public OptionalLong replayPoint() {
    if (samplingRate > 0 || samples == 0) return OptionalLong.empty();
    return OptionalLong.of(outsideRuns(earliestNeed()));
  }

  /** The earliest timestamp the state of this Detector, taken piece by piece, depends on. */
  private long earliestNeed() {
    long point = now;
    for (ChannelState track : tracks) point = Math.min(point, track.replayNeed());
    for (RuleState rule : rules) point = Math.min(point, rule.replayNeed());
    if (gaps) point = Math.min(point, lastSample);
    return point;
  }

  /** The latest timestamp at or before {@code start} that is not inside the run of a Rule. */
  private long outsideRuns(long start) {
    boolean moved = true;
    while (moved) {
      moved = false;
      for (int i = 0; i < runCount; i++) {
        if (runStarts[i] < start && start <= runEnds[i]) {
          start = runStarts[i];
          moved = true;
        }
      }
    }
    return start;
  }

  /**
   * A Rule's run, from the start a replay needs for it until {@code end}, is over: a replay may
   * not start inside it. The Detector keeps it until the Replay Point has passed it.
   */
  void forbidReplayStarts(long start, long end) {
    if (runCount == MAX_RUNS) {
      long keep = outsideRuns(earliestNeed());
      int kept = 0;
      for (int i = 0; i < runCount; i++) {
        if (runEnds[i] >= keep) {
          runStarts[kept] = runStarts[i];
          runEnds[kept++] = runEnds[i];
        }
      }
      runCount = kept;
      if (runCount > MAX_RUNS / 2) { // merge neighbours into pairs that cover both
        int merged = 0;
        for (int i = 0; i + 1 < runCount; i += 2) {
          runStarts[merged] = Math.min(runStarts[i], runStarts[i + 1]);
          runEnds[merged++] = Math.max(runEnds[i], runEnds[i + 1]);
        }
        if (runCount % 2 == 1) {
          runStarts[merged] = runStarts[runCount - 1];
          runEnds[merged++] = runEnds[runCount - 1];
        }
        runCount = merged;
      }
    }
    runStarts[runCount] = start;
    runEnds[runCount++] = end;
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
   * The number of entries this Detector currently holds: one per Channel a Rule or watched
   * Expression uses, one per value in each moving-average window and one per timestamp kept for
   * {@code count(...).within(...)} (timestamps older than the window are dropped at every
   * moment). A plain counter read, cheap to poll between pushes.
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

  void retain() {
    retained++;
  }

  void release() {
    retained--;
  }

  /** Counts an entry dropped from the full buffer named {@code what}; warns on the first one. */
  void recordDrop(String what) {
    if (dropped++ == 0) {
      System.getLogger("io.github.romanhaendler.rbed").log(System.Logger.Level.WARNING,
          what + ": a window reached the Retention Limit of " + retentionLimit
              + " entries; its oldest entries are dropped, so results of " + what
              + " deviate from an unlimited window."
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
    boolean outer = processing;
    processing = true;
    try {
      if (started) for (RuleState rule : rules) rule.close();
    } finally {
      processing = outer;
    }
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
    boolean outer = processing;
    processing = true;
    try {
      evaluate(timestamp, false);
    } finally {
      processing = outer;
    }
  }

  private void accept(long timestamp, Map<Channel, Double> values) {
    values.forEach(validateValue); // first, so a rejected Sample leaves no trace
    boolean advancedSince = samples > 0 && now > lastSample;
    moveTo(timestamp);
    for (ChannelState pulse : pulses) pulse.carried = false;
    sampleTimestamp = timestamp;
    values.forEach(applyValue);
    for (ChannelState pulse : pulses) if (!pulse.carried) pulse.pulseAbsent();
    boolean outer = processing;
    processing = true;
    try {
      evaluate(timestamp, true);
    } finally {
      processing = outer;
    }
    // a replay starting at the time advances between two Samples would not notice a gap in
    // between, which the Rules with a maxGap did
    if (gaps && advancedSince) forbidReplayStarts(lastSample, timestamp - 1);
    lastSample = timestamp;
    samples++;
  }

  /** Evaluates every Rule, in declaration order, at the current moment. */
  private void evaluate(long timestamp, boolean isSample) {
    // expired confirmation times go at once, so what is held depends on the Events, not on
    // which Rules happened to read their count
    for (EventState event : counted) event.dropExpired(timestamp);
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

  /** Starts recording which Channels, tracks and latest Events the Conditions being bound read. */
  void recordChannelLookups() {
    channelLookups = new LinkedHashSet<>();
    trackReads = new LinkedHashMap<>();
    latestReads = new LinkedHashSet<>();
  }

  /** A track (a Channel or a moving average) a Condition being bound reads, and how. */
  void read(ValueTrack track, int reads) {
    if (trackReads != null) trackReads.merge(track, reads, (a, b) -> a | b);
  }

  /** The Rule whose latest Event's Aggregates a Condition being bound reads. */
  void readsLatestOf(RuleState rule) {
    if (latestReads != null) latestReads.add(rule);
  }

  /** The tracks read since {@link #recordChannelLookups()}, with how they are read. */
  Map<ValueTrack, Integer> recordedTrackReads() {
    Map<ValueTrack, Integer> recorded = trackReads;
    trackReads = null;
    return recorded;
  }

  /** The Rules whose latest Event was read since {@link #recordChannelLookups()}. */
  RuleState[] recordedLatestReads() {
    RuleState[] recorded = latestReads.toArray(RuleState[]::new);
    latestReads = null;
    return recorded;
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
      state = new EventState(this, name);
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
      throw new IllegalArgumentException(binding + " references " + name
          + (added.contains(name) ? ", which is not added before it" : ", which is not added"));
    }
    EventState state = event(name);
    state.read = true;
    return state;
  }

  /** Builds a {@link Detector}. */
  public static final class Builder {
    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private final List<Event> history = new ArrayList<>();
    private final Set<Expression> watched = new LinkedHashSet<>();
    private byte[] restore;
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

    /**
     * Registers Expressions whose values the built Detector reports through
     * {@link Detector#value}. They are bound after all Rules and need not be used by any Rule;
     * their Channels count as used. May be called more than once; watching an Expression twice is
     * harmless.
     *
     * <p>Watching adds no allocation per Sample. Each moving average in a watched Expression
     * keeps a window of its own, even if a Rule uses the same instance: its entries take memory,
     * count in {@link Detector#retainedValues} and against the Retention Limit, and each push
     * updates it. Everything else in a watched Expression adds no work per push. Watching {@code latest(r).min/max/mean(c)}
     * makes Rule {@code r} aggregate Channel {@code c}, so {@code r}'s Events gain Aggregates of
     * {@code c}; which Events are found, and when, is unchanged.
     */
    public Builder watch(Expression... expressions) {
      for (Expression e : expressions) watched.add(Objects.requireNonNull(e, "expression"));
      return this;
    }

    /**
     * Makes {@link #build()} produce a Detector in the state of a {@link Detector#snapshot()}
     * instead of a fresh one, ready for the next Sample. The Rules, the sampling rate, the
     * Retention Limit and the watched Expressions of this Builder must be those of the Detector
     * that took the Snapshot. The listener is not part of a Snapshot: set it on this Builder.
     *
     * <p>{@code build()} throws {@link IllegalArgumentException}, with a message naming the cause,
     * if the array is not a Snapshot, is corrupted or truncated, has a format version this rbed
     * does not read, was taken with other Rules (also a renamed or changed Rule) or other options
     * (sampling rate, Retention Limit, watched Expressions), or if {@link #history} was also
     * called (a Snapshot already contains the History that was known).
     *
     * @throws NullPointerException if {@code snapshot} is null
     */
    public Builder restore(byte[] snapshot) {
      restore = Objects.requireNonNull(snapshot, "snapshot");
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
     * window keeps (default 1,000,000), and the maximum number of timestamps kept per counted Rule for
     * {@code count(...).within(...)}. When a full window or timestamp buffer gets a new entry, its
     * oldest entry is dropped, and the Detector logs one warning through {@link System.Logger}. See
     * {@link Detector#droppedValues}. {@code build()} rejects a Rule or watched Expression using
     * {@code averageOverSamples(n)} with {@code n} above the limit.
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
     * Builds the Detector and binds every Rule's Conditions and every watched Expression to it.
     *
     * @throws IllegalArgumentException if a Rule references itself, a Rule added after it or a
     *     Rule not added, if a watched Expression references a Rule not added, or if History
     *     contains an Event of no added Rule. A Rule without
     *     {@code when} and duplicate Rule names are already rejected by {@link #rules}.
     */
    public Detector build() {
      return new Detector(this);
    }
  }
}
