package rbed;

import java.util.Objects;
import java.util.function.DoubleBinaryOperator;

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

    /**
     * True for a Pulse Channel in a moment that does not carry it: comparisons on it are false,
     * not undefined, because the absence of a pulse is known.
     */
    default boolean absent() {
      return false;
    }
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

  /** This value plus {@code other}. */
  public Expression plus(Expression other) {
    return combine(this, other, (x, y) -> x + y);
  }

  /** This value plus {@code c}. */
  public Expression plus(double c) {
    return plus(constant(c));
  }

  /** This value minus {@code other}. */
  public Expression minus(Expression other) {
    return combine(this, other, (x, y) -> x - y);
  }

  /** This value minus {@code c}. */
  public Expression minus(double c) {
    return minus(constant(c));
  }

  /** This value times {@code other}. */
  public Expression times(Expression other) {
    return combine(this, other, (x, y) -> x * y);
  }

  /** This value times {@code c}. */
  public Expression times(double c) {
    return times(constant(c));
  }

  /** This value divided by {@code other}; undefined when {@code other} is 0. */
  public Expression dividedBy(Expression other) {
    return combine(this, other, (x, y) -> x / y);
  }

  /** This value divided by {@code c}; undefined when {@code c} is 0. */
  public Expression dividedBy(double c) {
    return dividedBy(constant(c));
  }

  /** The absolute value. */
  public Expression abs() {
    Expression self = this;
    return new Expression() {
      @Override Bound bind(Detector detector) {
        Bound inner = self.bind(detector);
        return () -> Math.abs(inner.value());
      }
    };
  }

  /** Undefined if an operand is undefined or the result is not finite. */
  private static Expression combine(Expression left, Expression right, DoubleBinaryOperator op) {
    Objects.requireNonNull(right, "other");
    return new Expression() {
      @Override Bound bind(Detector detector) {
        Bound x = left.bind(detector);
        Bound y = right.bind(detector);
        return () -> {
          double result = op.applyAsDouble(x.value(), y.value());
          return Double.isFinite(result) ? result : Double.NaN;
        };
      }
    };
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
            ? () -> x.absent() || y.absent() ? Truth.FALSE : Truth.greater(x.value(), y.value())
            : () -> x.absent() || y.absent() ? Truth.FALSE : Truth.less(x.value(), y.value());
      }
    };
  }
}
