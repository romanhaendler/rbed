package io.github.romanhaendler.rbed;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * One or more Conditions combined with timing logic that produces Events named after the Rule.
 * Rules are immutable: every configuration method returns a new Rule, so one Rule can be used in
 * several Detectors. Other Rules refer to it with {@link Conditions#during}, {@link
 * Conditions#after} and the like. {@link #forSamples} and {@link #forDuration} apply to the most
 * recent {@link #when} or {@link #endWhen}; when both are given, both must be met.
 */
public final class Rule {
  private final String name;
  final Phase when;
  final Phase end;
  final long maxGap;
  final Set<Channel> aggregated;
  private final boolean durationsApplyToEnd;
  private final String text;

  private Rule(String name, Phase when, Phase end, long maxGap, Set<Channel> aggregated,
      boolean durationsApplyToEnd) {
    this.name = name;
    this.when = when;
    this.end = end;
    this.maxGap = maxGap;
    this.aggregated = aggregated;
    this.durationsApplyToEnd = durationsApplyToEnd;
    this.text = describe(name, when, end, maxGap, aggregated);
  }

  private static String describe(
      String name, Phase when, Phase end, long maxGap, Set<Channel> aggregated) {
    StringBuilder text = new StringBuilder("Rule.named(\"").append(name).append("\")");
    if (when != null) {
      text.append(".when(").append(when.condition()).append(")").append(timing(when));
    }
    if (end != null) {
      text.append(".endWhen(").append(end.condition()).append(")").append(timing(end));
    }
    if (maxGap != Long.MAX_VALUE) text.append(".maxGap(").append(maxGap).append(")");
    if (!aggregated.isEmpty()) {
      text.append(".aggregate(").append(String.join(", ",
          aggregated.stream().map(Channel::toString).sorted().toList())).append(")");
    }
    return text.toString();
  }

  private static String timing(Phase phase) {
    return (phase.samples() > 0 ? ".forSamples(" + phase.samples() + ")" : "")
        + (phase.millis() > 0 ? ".forDuration(" + phase.millis() + ")" : "");
  }

  /** A Rule without Conditions yet; its name becomes the name of the Events it produces. */
  public static Rule named(String name) {
    if (Objects.requireNonNull(name, "name").isEmpty()) {
      throw new IllegalArgumentException("Rule name is empty");
    }
    return new Rule(name, null, null, Long.MAX_VALUE, Set.of(), false);
  }

  public String name() {
    return name;
  }

  /**
   * The Conditions under which an Event starts and, without an End Condition, continues. A new
   * Event only starts after these Conditions have stopped holding since the previous one.
   */
  public Rule when(Condition condition) {
    Phase phase = new Phase(Objects.requireNonNull(condition, "condition"));
    return new Rule(name, phase, end, maxGap, aggregated, false);
  }

  /**
   * An End Condition: once Confirmed, the Event continues until this holds (for its own
   * duration), regardless of {@link #when}. The Event's end is where this started holding.
   */
  public Rule endWhen(Condition condition) {
    Phase phase = new Phase(Objects.requireNonNull(condition, "condition"));
    return new Rule(name, when, phase, maxGap, aggregated, true);
  }

  /** The preceding Condition must hold for at least {@code n} consecutive Samples. */
  public Rule forSamples(int n) {
    if (n < 1) throw new IllegalArgumentException("forSamples must be >= 1: " + n);
    return updatePhase(phase -> phase.withSamples(n));
  }

  /** The preceding Condition must hold for at least {@code millis} milliseconds. */
  public Rule forDuration(long millis) {
    if (millis < 0) throw new IllegalArgumentException("forDuration must be >= 0: " + millis);
    return updatePhase(phase -> phase.withMillis(millis));
  }

  /** The preceding Condition must hold for at least {@code duration}. */
  public Rule forDuration(Duration duration) {
    return forDuration(duration.toMillis());
  }

  /**
   * If consecutive Samples are more than {@code millis} apart, a Pending Event is Discarded and a
   * Confirmed Event Ends, truncated, at the last Sample before the gap.
   */
  public Rule maxGap(long millis) {
    if (millis < 0) throw new IllegalArgumentException("maxGap must be >= 0: " + millis);
    return new Rule(name, when, end, millis, aggregated, durationsApplyToEnd);
  }

  /** See {@link #maxGap(long)}. */
  public Rule maxGap(Duration duration) {
    return maxGap(duration.toMillis());
  }

  /**
   * Also keeps Aggregates for these Channels. Channels referenced by the Rule's Conditions are
   * aggregated anyway.
   */
  public Rule aggregate(Channel... channels) {
    Set<Channel> all = new LinkedHashSet<>(aggregated);
    for (Channel channel : channels) all.add(Objects.requireNonNull(channel, "channel"));
    return new Rule(name, when, end, maxGap, all, durationsApplyToEnd);
  }

  private Rule updatePhase(UnaryOperator<Phase> change) {
    if (durationsApplyToEnd) {
      return new Rule(name, when, change.apply(end), maxGap, aggregated, true);
    }
    if (when == null) {
      throw new IllegalStateException("forSamples/forDuration must follow when() or endWhen()");
    }
    return new Rule(name, change.apply(when), end, maxGap, aggregated, false);
  }

  /**
   * Everything that defines this Rule's behaviour, written as in Java and in a fixed order, such
   * as {@code Rule.named("Hot").when(temp.above(80.0)).forSamples(3).maxGap(5000)}. Equal
   * configuration gives equal text (aggregated Channels sorted by name), on every run and
   * runtime. Expressions and Conditions name a Rule by {@link #name()} alone.
   */
  @Override
  public String toString() {
    return text;
  }
}
