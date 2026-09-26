package rbed;

/** Three-valued truth: a Condition is UNKNOWN while the data it needs is not available. */
enum Truth {
  TRUE, FALSE, UNKNOWN;

  static Truth of(boolean value) {
    return value ? TRUE : FALSE;
  }

  static Truth greater(double x, double y) {
    return Double.isNaN(x) || Double.isNaN(y) ? UNKNOWN : of(x > y);
  }

  static Truth less(double x, double y) {
    return Double.isNaN(x) || Double.isNaN(y) ? UNKNOWN : of(x < y);
  }

  /** FALSE if either is FALSE, else UNKNOWN if either is UNKNOWN, else TRUE. */
  Truth and(Condition.Bound other) {
    if (this == FALSE) return FALSE;
    Truth that = other.test();
    if (that == FALSE) return FALSE;
    return this == UNKNOWN || that == UNKNOWN ? UNKNOWN : TRUE;
  }

  /** TRUE if either is TRUE, else UNKNOWN if either is UNKNOWN, else FALSE. */
  Truth or(Condition.Bound other) {
    if (this == TRUE) return TRUE;
    Truth that = other.test();
    if (that == TRUE) return TRUE;
    return this == UNKNOWN || that == UNKNOWN ? UNKNOWN : FALSE;
  }

  Truth not() {
    return this == TRUE ? FALSE : this == FALSE ? TRUE : UNKNOWN;
  }
}
