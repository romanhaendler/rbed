package io.github.romanhaendler.rbed;

import java.util.Objects;

/**
 * A statement about the current Sample that is true, false or undefined (for instance while a
 * Channel has no value yet). Undefined counts as false wherever a Rule evaluates it and stays
 * undefined under {@link #not()}, so missing data never makes a Condition true. Conditions are
 * immutable descriptions; one Condition can be used by several Rules and Detectors. Create them
 * with {@link Conditions}.
 */
public abstract class Condition {
  private final String text;
  private final String key;

  Condition(String text, String key) {
    this.text = text;
    this.key = key;
  }

  /** The canonical encoding for the Snapshot fingerprint (see {@link Key}). */
  final String key() {
    return key;
  }

  /** Resolves this Condition against one Detector's state, once, when the Detector is built. */
  abstract Bound bind(Detector detector);

  /** A Condition bound to one Detector, evaluated once per Sample. */
  interface Bound {
    Truth test();
  }

  /** True when both are true; false when either is false; otherwise undefined. */
  public Condition and(Condition other) {
    Objects.requireNonNull(other, "other");
    Condition self = this;
    return new Condition(this + ".and(" + other + ")", Key.node("and", key, other.key)) {
      @Override Bound bind(Detector detector) {
        Bound left = self.bind(detector);
        Bound right = other.bind(detector);
        return () -> left.test().and(right);
      }
    };
  }

  /** True when either is true; false when both are false; otherwise undefined. */
  public Condition or(Condition other) {
    Objects.requireNonNull(other, "other");
    Condition self = this;
    return new Condition(this + ".or(" + other + ")", Key.node("or", key, other.key)) {
      @Override Bound bind(Detector detector) {
        Bound left = self.bind(detector);
        Bound right = other.bind(detector);
        return () -> left.test().or(right);
      }
    };
  }

  /** Negation; undefined stays undefined. */
  public Condition not() {
    Condition self = this;
    return new Condition(this + ".not()", Key.node("not", key)) {
      @Override Bound bind(Detector detector) {
        Bound inner = self.bind(detector);
        return () -> inner.test().not();
      }
    };
  }

  /**
   * How this Condition is written in Java, such as {@code temp.above(80.0)} or {@code
   * after(Maintenance).within(7776000000).not()}: Channels and Rules by name, constants as
   * {@code double}, durations in milliseconds. Deterministic and built once, so it identifies the
   * Condition.
   */
  @Override
  public String toString() {
    return text;
  }
}
