package rbed;

/**
 * A statement about the current Sample that is true, false or undefined (for instance when a
 * Channel has no value yet). Undefined counts as false wherever a Rule evaluates it, and stays
 * undefined under {@link #not()}, so missing data never makes a Condition true. Create Conditions
 * with {@link Conditions}.
 */
public abstract class Condition {
  Condition() {}

  /** Returns TRUE, FALSE or null for undefined. */
  abstract Boolean test(Detector d);

  /** True when both are true; false when either is false; otherwise undefined. */
  public Condition and(Condition other) {
    Condition self = this;
    return new Condition() {
      @Override Boolean test(Detector d) {
        Boolean a = self.test(d);
        if (Boolean.FALSE.equals(a)) return false;
        Boolean b = other.test(d);
        if (Boolean.FALSE.equals(b)) return false;
        return a == null || b == null ? null : true;
      }
    };
  }

  /** True when either is true; false when both are false; otherwise undefined. */
  public Condition or(Condition other) {
    Condition self = this;
    return new Condition() {
      @Override Boolean test(Detector d) {
        Boolean a = self.test(d);
        if (Boolean.TRUE.equals(a)) return true;
        Boolean b = other.test(d);
        if (Boolean.TRUE.equals(b)) return true;
        return a == null || b == null ? null : false;
      }
    };
  }

  /** Negation; undefined stays undefined. */
  public Condition not() {
    Condition self = this;
    return new Condition() {
      @Override Boolean test(Detector d) {
        Boolean a = self.test(d);
        return a == null ? null : !a;
      }
    };
  }
}
