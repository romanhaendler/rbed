package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.Expression;
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

  /** The Samples for the given parameter values; the same for every value unless overridden. */
  List<Sample> samples(Map<String, Double> values) {
    return samples();
  }

  /** Those of its Channels that are Pulse Channels; rbed does not tell a Channel's kind. */
  List<Channel> pulseChannels() {
    return List.of();
  }

  /** The Rules for the given value of every parameter, by parameter id. */
  abstract Rule[] rules(Map<String, Double> values);

  /**
   * Runs the Rules over {@code samples} (its own, with any Channel values the reader drew) with
   * {@code lifecycle} as the listener, setting {@code lifecycle.now} to each Sample's timestamp
   * before pushing it, and closes the Detector. A Scenario that feeds its Detector differently (a
   * sampling rate, time advances) overrides it and keeps {@code lifecycle.now} current the same way.
   */
  void detect(Rule[] rules, List<Sample> samples, Lifecycle lifecycle) {
    Detector detector = Detector.builder().rules(rules).listener(lifecycle).build();
    for (Sample sample : samples) {
      lifecycle.now = sample.timestamp();
      detector.push(sample.timestamp(), sample.values());
    }
    detector.close();
  }

  /**
   * Like {@link #detect(Rule[], List, Lifecycle)}, for a Scenario whose feeding depends on its
   * parameters (a sampling rate, a timer advancing time, a restart with or without History).
   * The bridge calls this one.
   */
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    detect(rules, samples, lifecycle);
  }

  /**
   * What the Scenario's listener makes of an Event at the given parameter values, such as the
   * class of a lifted load; or null.
   */
  String note(Event event, Map<String, Double> values) {
    return null;
  }

  /** The Expression to plot for the given parameter values, or null for none. */
  Plot plot(Map<String, Double> values) {
    return null;
  }

  /** An Expression plotted next to the Channels, with the threshold its Condition compares to. */
  record Plot(String name, Expression expression, double threshold) {}

  /**
   * A number the reader can change, with its default and the limits of its control. A choice has
   * options: its value is the index of the chosen one, and {@code ${id}} in the source stands for
   * that option's source, which may hold placeholders of its own.
   */
  record Parameter(String id, String label, double defaultValue, double min, double max,
      double step, String unit, List<Option> options) {
    Parameter(String id, String label, double defaultValue, double min, double max, double step,
        String unit) {
      this(id, label, defaultValue, min, max, step, unit, List.of());
    }

    static Parameter choice(String id, String label, int defaultIndex, List<Option> options) {
      return new Parameter(id, label, defaultIndex, 0, options.size() - 1, 1, "", options);
    }
  }

  /** One option of a choice: its label and the Java source it stands for. */
  record Option(String label, String source) {}
}
