package io.github.romanhaendler.rbed;

/** Ascending timestamps in a growable ring buffer; old ones are dropped from the front. */
final class Timestamps {
  private final Detector detector;
  /** The Rule that first uses the count, named in the Retention Limit warning. */
  private final String rule;
  private long[] times;
  private int head;
  private int size;

  Timestamps(Detector detector) {
    this.detector = detector;
    rule = detector.bindingRule;
    times = new long[Math.min(16, detector.retentionLimit())];
  }

  /** Adds {@code t}; at the Retention Limit the oldest timestamp is dropped to make room. */
  void add(long t) {
    if (size == detector.retentionLimit()) {
      head = (head + 1) % times.length;
      size--;
      detector.retained--;
      detector.dropped(rule);
    }
    if (size == times.length) {
      long[] grown = new long[Math.min(times.length * 2, detector.retentionLimit())];
      for (int i = 0; i < size; i++) grown[i] = times[(head + i) % times.length];
      times = grown;
      head = 0;
    }
    times[(head + size) % times.length] = t;
    size++;
    detector.retained++;
  }

  /** Drops timestamps more than {@code window} before {@code now}. */
  void dropOlderThan(long now, long window) {
    while (size > 0 && now - times[head] > window) {
      head = (head + 1) % times.length;
      size--;
      detector.retained--;
    }
  }

  /** The number of timestamps at most {@code window} before {@code now}. */
  int countWithin(long now, long window) {
    int n = 0;
    for (int i = size - 1; i >= 0 && now - times[(head + i) % times.length] <= window; i--) n++;
    return n;
  }
}
