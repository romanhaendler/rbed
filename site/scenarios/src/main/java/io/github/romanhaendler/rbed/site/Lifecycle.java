package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.EventListener;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The listener the bridge registers, recording the callbacks the test Recorder records. An Ended
 * Event carries its start (where it went Pending), Confirmed and end timestamps; a Discarded
 * Pending Event is kept with the moment it was Discarded, which the Event itself does not carry.
 */
final class Lifecycle implements EventListener {
  record Discard(Event event, long at) {}

  final List<Event> ended = new ArrayList<>();
  final List<Discard> discarded = new ArrayList<>();
  /** The timestamp of the Sample being pushed; close() Discards at the last one. */
  long now;
  /** The timestamps the Detector was advanced to without a Sample, for the chart. */
  final List<Long> advances = new ArrayList<>();
  /** The timestamp of the first Sample after the Detector was restarted, or null. */
  Long restart;
  /** Notes a Scenario's listener made of Events during this run, by Event name and start. */
  private final Map<String, String> notes = new HashMap<>();

  /** Records what the Scenario's listener made of an Event, for the Event table's Note. */
  void note(Event event, String note) {
    notes.put(event.name() + " " + event.start(), note);
  }

  /** The note recorded for the Event (by its name and start), or null. */
  String noteOf(Event event) {
    return notes.get(event.name() + " " + event.start());
  }

  /** Pushes the Sample to the Detector, keeping {@code now}. */
  void push(Detector detector, Sample sample) {
    now = sample.timestamp();
    detector.push(sample.timestamp(), sample.values());
  }

  /**
   * Pushes the Sample's values to a Detector with a sampling rate; the Sample's timestamp must be
   * the one the Detector derives, which becomes {@code now}.
   */
  void pushValues(Detector detector, Sample sample) {
    now = sample.timestamp();
    detector.push(sample.values());
  }

  /** Advances the Detector to {@code timestamp}, keeping {@code now} and the advances. */
  void advance(Detector detector, long timestamp) {
    now = timestamp;
    detector.advanceTo(timestamp);
    advances.add(timestamp);
  }

  @Override
  public void onEnded(Event event) {
    ended.add(event);
  }

  @Override
  public void onDiscarded(Event event) {
    discarded.add(new Discard(event, now));
  }
}
