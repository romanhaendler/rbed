package io.github.romanhaendler.rbed;

import java.util.ArrayList;
import java.util.List;

/** Test listener that records lifecycle callbacks as readable lines, e.g. "confirmed Lift 0". */
final class Recorder implements EventListener {
  final List<String> log = new ArrayList<>();
  final List<Event> ended = new ArrayList<>();

  /** Min, max, mean and count as text, to compare the Sample-based part of Aggregates. */
  static String stats(double min, double max, double mean, long count) {
    return "min=" + min + " max=" + max + " mean=" + mean + " count=" + count;
  }

  static String stats(Aggregates a) {
    return stats(a.min(), a.max(), a.mean(), a.count());
  }

  /** Only the "ended ..." lines of the log. */
  List<String> endedLines() {
    return log.stream().filter(line -> line.startsWith("ended")).toList();
  }

  @Override public void onPending(Event e) { log.add("pending " + e.name() + " " + e.start()); }
  @Override public void onConfirmed(Event e) { log.add("confirmed " + e.name() + " " + e.start()); }
  @Override public void onDiscarded(Event e) { log.add("discarded " + e.name() + " " + e.start()); }
  @Override public void onEnded(Event e) {
    ended.add(e);
    log.add("ended " + e.name() + " " + e.start() + ".." + e.end().getAsLong()
        + (e.truncatedStart() ? " truncatedStart" : "")
        + (e.truncatedEnd() ? " truncatedEnd" : ""));
  }
}
