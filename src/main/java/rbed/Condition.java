package rbed;

/**
 * A statement about the current Sample that is true, false or undefined (for instance when a
 * Channel has no value yet). Undefined counts as false wherever a Rule evaluates it, and stays
 * undefined under {@link #not()}, so missing data never makes a Condition true. Create Conditions
 * with {@link Conditions}.
 */
public abstract class Condition {
  private final Object[] children;

  /** @param children the Conditions and Expressions this one is built from */
  Condition(Object... children) {
    this.children = children;
  }

  /** Visits this node and, depth first, every Condition and Expression it is built from. */
  void visit(java.util.function.Consumer<Object> visitor) {
    visitor.accept(this);
    for (Object child : children) {
      if (child instanceof Condition c) c.visit(visitor);
      else ((Expression) child).visit(visitor);
    }
  }

  /** Returns TRUE, FALSE or null for undefined. */
  abstract Boolean test(Detector d);

  /** True when both are true; false when either is false; otherwise undefined. */
  public Condition and(Condition other) {
    Condition self = this;
    return new Condition(self, other) {
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
    return new Condition(self, other) {
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
    return new Condition(self) {
      @Override Boolean test(Detector d) {
        Boolean a = self.test(d);
        return a == null ? null : !a;
      }
    };
  }
}
