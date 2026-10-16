package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Channel;
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

  /** The Rules for the given value of every parameter, by parameter id. */
  abstract Rule[] rules(Map<String, Double> values);

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
