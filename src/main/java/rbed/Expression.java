package rbed;

/** A numeric value derived from the current Sample. Create Expressions with {@link Conditions}. */
public abstract class Expression {
  Expression() {}

  /** Returns the current value, or NaN when undefined. */
  abstract double value(Detector d);

  /** True when this value is strictly greater than {@code limit}. */
  public Condition above(double limit) {
    return compare(this, constant(limit), 1);
  }

  static Expression constant(double c) {
    return new Expression() {
      @Override double value(Detector d) { return c; }
    };
  }

  static Condition compare(Expression a, Expression b, int sign) {
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
