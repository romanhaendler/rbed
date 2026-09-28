package io.github.romanhaendler.rbed;

/**
 * A Condition together with how long it must hold: for at least {@code samples} consecutive
 * Samples and at least {@code millis} milliseconds. The defaults (0 Samples, 0 ms) mean "holds
 * now", also at a time advance, which is not a Sample.
 */
record Phase(Condition condition, int samples, long millis) {
  Phase(Condition condition) {
    this(condition, 0, 0);
  }

  Phase withSamples(int n) {
    return new Phase(condition, n, millis);
  }

  Phase withMillis(long ms) {
    return new Phase(condition, samples, ms);
  }
}
