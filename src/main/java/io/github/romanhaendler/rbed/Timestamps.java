package io.github.romanhaendler.rbed;

/** Ascending timestamps in a growable ring buffer; old ones are dropped from the front. */
final class Timestamps {
  private final Detector detector;
  /** Names the buffer in the Retention Limit warning, e.g. "count of Run". */
  private final String rule;
  private long[] times;
  private int head;
  private int size;
  /** Sequence number of the head: how many timestamps have ever left the front. */
  private long base;

  Timestamps(Detector detector, String counted) {
    this.detector = detector;
    rule = "count of " + counted;
    times = new long[Math.min(16, detector.retentionLimit())];
  }

  /** Adds {@code t}; at the Retention Limit the oldest timestamp is dropped to make room. */
  void add(long t) {
    if (size == detector.retentionLimit()) {
      head = (head + 1) % times.length;
      size--;
      base++;
      detector.release();
      detector.recordDrop(rule);
    }
    if (size == times.length) {
      long[] grown = new long[Math.min(times.length * 2, detector.retentionLimit())];
      for (int i = 0; i < size; i++) grown[i] = times[(head + i) % times.length];
      times = grown;
      head = 0;
    }
    times[(head + size) % times.length] = t;
    size++;
    detector.retain();
  }

  /** Writes the timestamps oldest first; the ring's position and capacity are not state. */
  void save(StateWriter w) {
    w.int32(size);
    for (int i = 0; i < size; i++) w.int64(times[(head + i) % times.length]);
    w.int64(base);
  }

  void load(StateReader r) throws java.io.IOException {
    int n = r.length();
    if (n > detector.retentionLimit()) throw new java.io.IOException("too many timestamps");
    times = new long[Math.max(times.length, n)];
    for (int i = 0; i < n; i++) times[i] = r.int64();
    size = n; // a restore only happens in a Detector just built, so head is 0
    base = r.int64();
  }

  /** Drops timestamps more than {@code window} before {@code now}. */
  void dropOlderThan(long now, long window) {
    while (size > 0 && now - times[head] > window) {
      head = (head + 1) % times.length;
      size--;
      base++;
      detector.release();
    }
  }

  /**
   * Counts timestamps at most {@code window} before a moving "now" in amortized constant time.
   * Remembers where its window starts as a sequence number, which stays valid when timestamps
   * are dropped; time only moves forward, so the position only moves forward.
   */
  final class Counter {
    private final long window;
    private long start;

    Counter(long window) {
      this.window = window;
    }

    int countWithin(long now) {
      if (start < base) start = base;
      long end = base + size;
      while (start < end && now - times[(int) ((head + start - base) % times.length)] > window) {
        start++;
      }
      return (int) (end - start);
    }
  }
}
