package rbed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private long samples;
  private long lastTimestamp;
  private boolean closed;

  private Detector(Builder b) {
    listener = b.listener;
    rules = new ArrayList<>();
    b.rules.forEach((name, rb) -> rules.add(new Rule(name, rb)));
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

  double channelValue(String channel) {
    Double v = values.get(channel);
    return v == null ? Double.NaN : v;
  }

  /** Builds a {@link Detector}. */
  public static final class Builder {
    private final Map<String, RuleBuilder> rules = new java.util.LinkedHashMap<>();
    private EventListener listener = new EventListener() {};

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

    public Detector build() {
      return new Detector(this);
    }
  }
}
