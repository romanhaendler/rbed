package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
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

  /** The name of the Rule whose latest Event the Scenario's Rules read, or null for none. */
  String readsLatest() {
    return null;
  }

  /** The Rules for the given value of every parameter, by parameter id. */
  abstract Rule[] rules(Map<String, Double> values);

  /**
   * A value at every Sample, plotted against the limit a Rule compares it with, such as a running
   * count of Events. rbed does not expose an Expression's value, so it is derived from the Events
   * rbed reported; BridgeTest checks that it is above its limit exactly while that Rule's Events
   * run. None by default.
   */
  Running running(Map<String, Double> values, List<Sample> samples, List<Event> events) {
    return null;
  }

  /** The value of {@link #running} at every Sample, its label and unit, and its limit. */
  record Running(String label, String unit, double limit, double[] values) {}

  /** A number the reader can change, with its default and the limits of its control. */
  record Parameter(String id, String label, double defaultValue, double min, double max,
      double step, String unit) {}
}
