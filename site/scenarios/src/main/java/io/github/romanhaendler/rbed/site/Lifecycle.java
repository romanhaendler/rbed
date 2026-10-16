package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.EventListener;
import java.util.ArrayList;
import java.util.List;

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

  @Override
  public void onEnded(Event event) {
    ended.add(event);
  }

  @Override
  public void onDiscarded(Event event) {
    discarded.add(new Discard(event, now));
  }
}
