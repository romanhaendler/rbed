package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.List;
import java.util.Map;

/**
 * A parameterised example shown on the site: its Channels, its Samples, and Rules built from
 * parameter values. {@code source} is the Java source of the Rules, with a placeholder such as
 * {@code ${threshold}} for each parameter, which the site replaces by the current value.
 */
abstract class Scenario {
  final String id;
  final String title;
  /** Name of the time unit the site shows timestamps in, and its length in milliseconds. */
  final String timeUnit;
  final long timeUnitMillis;
  final List<Channel> channels;
  final List<Parameter> parameters;
  final String source;

  Scenario(String id, String title, String timeUnit, long timeUnitMillis, List<Channel> channels,
      List<Parameter> parameters, String source) {
    this.id = id;
    this.title = title;
    this.timeUnit = timeUnit;
    this.timeUnitMillis = timeUnitMillis;
    this.channels = channels;
    this.parameters = parameters;
    this.source = source;
  }

  abstract List<Sample> samples();

  /** Those of its Channels that are Pulse Channels; rbed does not tell a Channel's kind. */
  List<Channel> pulseChannels() {
    return List.of();
  }

  /** The Rules for the given value of every parameter, by parameter id. */
  abstract Rule[] rules(Map<String, Double> values);

  /**
   * Runs the Rules over the Samples and returns the Ended Events in the order they Ended. A
   * Scenario that feeds its Detector differently (a sampling rate, time advances) overrides it.
   */
  List<Event> detect(Rule[] rules) {
    return Detector.builder().rules(rules).build().detect(samples());
  }

  /**
   * What the Scenario's listener makes of an Event at the given parameter values, such as the
   * class of a lifted load; or null.
   */
  String note(Event event, Map<String, Double> values) {
    return null;
  }

  /** A number the reader can change, with its default and the limits of its control. */
  record Parameter(String id, String label, double defaultValue, double min, double max,
      double step, String unit) {}
}
