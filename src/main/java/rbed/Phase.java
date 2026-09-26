package rbed;

/**
 * A Condition together with how long it must hold: for at least {@code samples} consecutive
 * Samples and at least {@code millis} milliseconds. The defaults (1 Sample, 0 ms) mean "holds now".
 */
record Phase(Condition condition, int samples, long millis) {
  Phase(Condition condition) {
    this(condition, 1, 0);
  }

  Phase withSamples(int n) {
    return new Phase(condition, n, millis);
  }

  Phase withMillis(long ms) {
    return new Phase(condition, samples, ms);
  }
}
