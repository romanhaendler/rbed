package rbed;

/** A numeric value derived from the current Sample. Create Expressions with {@link Conditions}. */
public abstract class Expression {
  Expression() {}

  /** Returns the current value, or NaN when undefined. */
  abstract double value(Detector d);

  /** True when this value is strictly greater than {@code limit}. */
  public Condition above(double limit) {
    return above(constant(limit));
  }

  /** True when this value is strictly greater than {@code other}. */
  public Condition above(Expression other) {
    return compare(this, other, 1);
  }

  /** True when this value is strictly less than {@code limit}. */
  public Condition below(double limit) {
    return below(constant(limit));
  }

  /** True when this value is strictly less than {@code other}. */
  public Condition below(Expression other) {
    return compare(this, other, -1);
  }

  /** True when {@code low <= value <= high}. */
  public Condition between(double low, double high) {
    return compare(this, constant(low), -1).not().and(compare(this, constant(high), 1).not());
  }

  static Expression constant(double c) {
    return new Expression() {
      @Override double value(Detector d) { return c; }
    };
  }

  private static Condition compare(Expression a, Expression b, int sign) {
    return new Condition() {
      @Override Boolean test(Detector d) {
        double x = a.value(d);
        double y = b.value(d);
        if (Double.isNaN(x) || Double.isNaN(y)) return null;
        return Double.compare(x, y) == sign;
      }
    };
  }
}
