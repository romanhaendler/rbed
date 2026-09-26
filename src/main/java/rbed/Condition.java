package rbed;

/**
 * A statement about the current Sample that is true, false or undefined (for instance when a
 * Channel has no value yet). Undefined counts as false wherever a Rule evaluates it. Create
 * Conditions with {@link Conditions}.
 */
public abstract class Condition {
  Condition() {}

  /** Returns TRUE, FALSE or null for undefined. */
  abstract Boolean test(Detector d);
}
