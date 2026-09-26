package rbed;

import java.util.Objects;

/**
 * A numeric value derived from the current Sample; undefined while the data it needs is not
 * available. Create Expressions with {@link Conditions#channel}.
 */
public abstract class Expression {
  Expression() {}

  /** Resolves this Expression against one Detector's state, once, when the Detector is built. */
  abstract Bound bind(Detector detector);

  /** An Expression bound to one Detector. */
  interface Bound {
    /** The current value, or NaN when undefined. */
    double value();
  }

  /** True when this value is strictly greater than {@code limit}. */
  public Condition above(double limit) {
    return above(constant(limit));
  }

  /** True when this value is strictly greater than {@code other}. */
  public Condition above(Expression other) {
    return comparison(this, other, true);
  }

  /** True when this value is strictly less than {@code limit}. */
  public Condition below(double limit) {
    return below(constant(limit));
  }

  /** True when this value is strictly less than {@code other}. */
  public Condition below(Expression other) {
    return comparison(this, other, false);
  }

  /** True when {@code low <= value <= high}. */
  public Condition between(double low, double high) {
    requireFinite(low);
    requireFinite(high);
    if (low > high) throw new IllegalArgumentException("between: " + low + " > " + high);
    return below(low).or(above(high)).not();
  }

  private static Expression constant(double value) {
    requireFinite(value);
    return new Expression() {
      @Override Bound bind(Detector detector) {
        return () -> value;
      }
    };
  }

  private static void requireFinite(double limit) {
    if (!Double.isFinite(limit)) throw new IllegalArgumentException("Limit is " + limit);
  }

  private static Condition comparison(Expression left, Expression right, boolean greater) {
    Objects.requireNonNull(right, "other");
    return new Condition() {
      @Override Bound bind(Detector detector) {
        Expression.Bound x = left.bind(detector);
        Expression.Bound y = right.bind(detector);
        return greater
            ? () -> Truth.greater(x.value(), y.value())
            : () -> Truth.less(x.value(), y.value());
      }
    };
  }
}
