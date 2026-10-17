package io.github.romanhaendler.rbed.examples;

import static io.github.romanhaendler.rbed.Conditions.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.EventListener;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;

/**
 * Resuming a stream: a consumer that stops after any message and continues as if it had never
 * stopped. An in-memory list stands in for the topic (the offset is the index), a {@link Store}
 * for the database, and the output list for the side effects of the listener (alerts, rows).
 * The three restart procedures of the README each produce exactly the uninterrupted output.
 *
 * <ul>
 *   <li>Overheat: temperature above 80 for 3 s, over when the load drops below 10, which can
 *       happen while the temperature is still high.
 *   <li>Warm: the average of the last 5 temperatures above 60 for 3 Samples.
 *   <li>Repeated: more than one Overheat Confirmed within a minute.
 * </ul>
 */
class ResumingAStreamExample {
  static final Channel TEMP = Channel.named("temp");
  static final Channel LOAD = Channel.named("load");

  static final Rule OVERHEAT = Rule.named("Overheat")
      .when(TEMP.above(80)).forDuration(3_000)
      .endWhen(LOAD.below(10));
  static final Rule WARM = Rule.named("Warm")
      .when(TEMP.averageOverSamples(5).above(60)).forSamples(3);
  static final Rule REPEATED = Rule.named("Repeated")
      .when(count(OVERHEAT).within(60_000).above(1));

  /** The topic: one Sample per second; the offset of a message is its index. */
  static List<Sample> topic() {
    List<Sample> messages = new ArrayList<>();
    for (int s = 0; s < 120; s++) {
      double temp = s < 10 ? 40 : s < 30 ? 90 : s < 40 ? 50 : s < 70 ? 85 : s < 90 ? 45 : s < 100 ? 88 : 45;
      double load = (s >= 20 && s < 35) || (s >= 60 && s < 80) || s >= 100 ? 5 : 50;
      messages.add(new Sample(s * 1_000L, Map.of(TEMP, temp, LOAD, load)));
    }
    return messages;
  }

  // README-BEGIN
  /** What the application keeps in its database, written together with each message's effects. */
  static final class Store {
    byte[] snapshot;                  // the latest Snapshot ...
    int snapshotNext;                 // ... and the offset of the next message after it
    int next;                         // the next message to process: everything before is done
    OptionalLong replayPoint = OptionalLong.empty();       // for the replay recipe
    final Map<String, Event> ended = new TreeMap<>();      // Ended Events, keyed by Rule and start
  }

  /** One consumer: reads the topic from {@code next} on and reports what the Detector finds. */
  static final class Consumer implements EventListener {
    final List<Sample> topic;
    final List<String> output;        // the side effects: alerts, rows, ...
    final Store store;
    final int snapshotEvery;
    Detector detector;
    int next;                         // offset of the message being processed
    int quietUntil;                   // replayed messages: their effects were done before

    Consumer(List<Sample> topic, List<String> output, Store store, int snapshotEvery) {
      this.topic = topic;
      this.output = output;
      this.store = store;
      this.snapshotEvery = snapshotEvery;
    }

    Detector.Builder builder() {
      return Detector.builder().rules(OVERHEAT, WARM, REPEATED).listener(this);
    }

    @Override public void onConfirmed(Event e) { report("confirmed", e); }
    @Override public void onEnded(Event e) {
      store.ended.put(e.name() + "@" + e.start(), e);
      report("ended", e);
    }

    private void report(String kind, Event e) {
      if (next >= quietUntil) output.add(kind + " " + e.name() + " " + e.start());
    }

    /** Processes messages up to (not including) {@code end}, keeping the Store up to date. */
    void run(int end) {
      for (; next < end; next++) {
        Sample message = topic.get(next);
        detector.push(message.timestamp(), message.values());
        store.next = next + 1;
        store.replayPoint = detector.replayPoint();
        if (store.next % snapshotEvery == 0) {
          store.snapshot = detector.snapshot();
          store.snapshotNext = store.next;
        }
      }
    }

    /** First start: nothing is stored yet. */
    static Consumer fresh(List<Sample> topic, List<String> output, Store store, int every) {
      Consumer c = new Consumer(topic, output, store, every);
      c.detector = c.builder().build();
      return c;
    }

    /** Restart 1 and 3: restore the latest Snapshot, replay the messages after it (if any). */
    static Consumer restore(List<Sample> topic, List<String> output, Store store, int every) {
      Consumer c = new Consumer(topic, output, store, every);
      c.detector = store.snapshot == null            // none taken yet: replay everything
          ? c.builder().build() : c.builder().restore(store.snapshot).build();
      c.next = store.snapshot == null ? 0 : store.snapshotNext;
      c.quietUntil = store.next;      // the effects of the tail were done before the crash
      return c;
    }

    /** Restart 2: History of the Events Ended before the Replay Point, replay from there. */
    static Consumer replay(List<Sample> topic, List<String> output, Store store, int every) {
      Consumer c = new Consumer(topic, output, store, every);
      long from = store.replayPoint.orElse(Long.MIN_VALUE);
      List<Event> history = store.ended.values().stream()
          .filter(e -> e.end().getAsLong() < from).toList();
      c.detector = c.builder().history(history).build();
      while (c.next < topic.size() && topic.get(c.next).timestamp() < from) c.next++;
      c.quietUntil = store.next;
      return c;
    }
  }
  // README-END

  interface Restart {
    Consumer resume(List<Sample> topic, List<String> output, Store store, int every);
  }

  static final List<String> EXPECTED = List.of(
      "confirmed Overheat 10000",
      "confirmed Warm 12000",
      "ended Overheat 10000",
      "ended Warm 12000",
      "confirmed Overheat 40000",
      "confirmed Warm 41000",
      "confirmed Repeated 43000",
      "ended Overheat 40000",
      "ended Warm 41000",
      "ended Repeated 43000",
      "confirmed Overheat 90000",
      "confirmed Warm 91000",
      "confirmed Repeated 93000",
      "ended Overheat 90000",
      "ended Warm 91000",
      "ended Repeated 93000");

  /** The output of a consumer that never stops. */
  static List<String> uninterrupted(List<Sample> topic) {
    List<String> output = new ArrayList<>();
    Consumer.fresh(topic, output, new Store(), 1).run(topic.size());
    return output;
  }

  /** Stops after every possible message, restarts with {@code restart} and finishes the topic. */
  private static void everyStop(Restart restart, int snapshotEvery) {
    List<Sample> topic = topic();
    List<String> expected = uninterrupted(topic);
    assertEquals(EXPECTED, expected);
    int replayed = 0;
    for (int stop = 0; stop <= topic.size(); stop++) {
      List<String> output = new ArrayList<>();
      Store store = new Store();
      Consumer.fresh(topic, output, store, snapshotEvery).run(stop);   // ... then it crashes
      Consumer second = restart.resume(topic, output, store, snapshotEvery);
      if (second.next < stop) replayed++;
      second.run(topic.size());
      assertEquals(expected, output, "stop after " + stop + " messages");
    }
    // the older Snapshot and the Replay Point really make the second run replay messages
    assertTrue(replayed > 0 || snapshotEvery == 1, "nothing was replayed");
  }

  @Test
  void restoreAndContinue() {
    everyStop(Consumer::restore, 1);
  }

  @Test
  void replayFromTheReplayPoint() {
    everyStop(Consumer::replay, 1_000);
  }

  @Test
  void anOlderSnapshotPlusTheTail() {
    everyStop(Consumer::restore, 7);
  }
}
