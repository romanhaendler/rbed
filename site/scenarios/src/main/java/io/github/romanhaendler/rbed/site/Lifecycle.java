package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.EventListener;
import io.github.romanhaendler.rbed.Expression;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The listener the bridge registers, recording the callbacks the test Recorder records. An Ended
 * Event carries its start (where it went Pending), Confirmed and end timestamps; a Discarded
 * Pending Event is kept with the moment it was Discarded, which the Event itself does not carry.
 * After every Sample and every advance, it also reads the plotted Expressions from the Detector.
 */
class Lifecycle implements EventListener {
  record Discard(Event event, long at) {}

  final List<Event> ended = new ArrayList<>();
  final List<Discard> discarded = new ArrayList<>();
  /** The timestamp of the Sample being pushed; close() Discards at the last one. */
  long now;
  /** The timestamps the Detector was advanced to without a Sample, for the chart. */
  final List<Long> advances = new ArrayList<>();
  /** The timestamp of the first Sample after the Detector was restarted, or null. */
  Long restart;
  /** Whether the restarted Detector started without History, knowing no earlier Event. */
  boolean restartForgets;
  /** The Expressions the bridge plots, which every Detector of the run watches. */
  private final Expression[] plotted;
  /** The plotted Expressions' values after each Sample, in order; NaN where undefined. */
  final List<double[]> atSamples = new ArrayList<>();
  /** The plotted Expressions' values after each advance, in order; NaN where undefined. */
  final List<double[]> atAdvances = new ArrayList<>();
  /** Notes a Scenario's listener made of Events during this run, by Event name and start. */
  private final Map<String, String> notes = new HashMap<>();

  /** A Lifecycle for a run that plots these Expressions. */
  Lifecycle(List<Expression> plotted) {
    this.plotted = plotted.toArray(Expression[]::new);
  }

  /**
   * A builder for every Detector of the run: with this Lifecycle as its listener (a Scenario
   * with a listener of its own passes every callback on to it) and watching the plotted
   * Expressions.
   */
  Detector.Builder builder() {
    return Detector.builder().listener(this).watch(plotted);
  }

  /** Records what the Scenario's listener made of an Event, for the Event table's Note. */
  void note(Event event, String note) {
    notes.put(event.name() + " " + event.start(), note);
  }

  /** The note recorded for the Event (by its name and start), or null. */
  String noteOf(Event event) {
    return notes.get(event.name() + " " + event.start());
  }

  /**
   * Records that the Detector was restarted at {@code at}, with the Events Ended so far as
   * History or without.
   */
  void restart(long at, boolean withHistory) {
    restart = at;
    restartForgets = !withHistory;
  }

  /** Pushes the Sample to the Detector, keeping {@code now}. */
  void push(Detector detector, Sample sample) {
    now = sample.timestamp();
    detector.push(sample.timestamp(), sample.values());
    read(detector, atSamples);
  }

  /**
   * Pushes the Sample's values to a Detector with a sampling rate; the Sample's timestamp must be
   * the one the Detector derives, which becomes {@code now}.
   */
  void pushValues(Detector detector, Sample sample) {
    now = sample.timestamp();
    detector.push(sample.values());
    read(detector, atSamples);
  }

  /** Advances the Detector to {@code timestamp}, keeping {@code now} and the advances. */
  void advance(Detector detector, long timestamp) {
    now = timestamp;
    detector.advanceTo(timestamp);
    advances.add(timestamp);
    read(detector, atAdvances);
  }

  /** Reads every plotted Expression from the Detector into {@code moments}. */
  private void read(Detector detector, List<double[]> moments) {
    double[] values = new double[plotted.length];
    for (int i = 0; i < plotted.length; i++) {
      values[i] = detector.value(plotted[i]).orElse(Double.NaN) + 0.0;  // -0.0 is plotted as 0
    }
    moments.add(values);
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
