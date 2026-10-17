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
 *
 * <p>A Scenario class is named after its page ({@code Cranes}, {@code During}, {@code Count}),
 * with the suffix {@code Scenario} only where that name is a public rbed type
 * ({@code AfterScenario}, {@code AggregatesScenario}).
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

  /** The Samples for the given value of every parameter, by parameter id. */
  abstract List<Sample> samples(Map<String, Double> values);

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
   * Runs the Rules over {@code samples} (its own, with any Channel values the reader drew) with
   * {@code lifecycle} as the listener, feeding each Sample through {@link Lifecycle#push}, and
   * closes the Detector. A Scenario whose feeding depends on its parameters or is otherwise
   * different (a sampling rate, time advances, a restart with or without History) overrides it
   * and feeds through {@code lifecycle} the same way ({@code push}, {@code pushValues},
   * {@code advance}), so the bridge sees every moment.
   */
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    Detector detector = Detector.builder().rules(rules).listener(lifecycle).build();
    for (Sample sample : samples) lifecycle.push(detector, sample);
    detector.close();
  }

  /**
   * What the Scenario's listener makes of an Event at the given parameter values, such as the
   * class of a lifted load; or null. A listener that needs more than the Event (the end of an
   * earlier one) notes it on the {@link Lifecycle} during {@code detect} instead, so the note
   * travels with the run and the Scenario keeps no state.
   */
  String note(Event event, Map<String, Double> values) {
    return null;
  }

  /** The Expressions to plot for the given parameter values: by default none. */
  List<Plot> plots(Map<String, Double> values) {
    return List.of();
  }

  /**
   * Where the Rules compare a Channel with a value, for the given parameter values; the chart
   * draws each as a line on that Channel's axis.
   */
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of();
  }

  /**
   * The windows the Rules' {@code after(rule).within(...)} open, for the given parameter values;
   * the chart shades each from the end of every Event of that Rule.
   */
  List<Window> windows(Map<String, Double> values) {
    return List.of();
  }

  /**
   * An Expression plotted next to the Channels, with the threshold its Condition compares to: the
   * Rule that compares (or several, as in {@link Threshold}), the part of the Rule and the
   * comparison ({@code "when >"}), and the value. One with a {@code unit} of its own (a count of
   * Events, a duration) is plotted on its own axis, under the Events. The Expression may refer to
   * the Scenario's Rules.
   */
  record Plot(String name, Expression expression, String rule, String condition,
      double threshold, String unit) {
    Plot(String name, Expression expression, String rule, String condition, double threshold) {
      this(name, expression, rule, condition, threshold, null);
    }

    /** One the Rules compare with another Expression, not with a value: it has no threshold. */
    Plot(String name, Expression expression) {
      this(name, expression, null, null, Double.NaN, null);
    }
  }

  /**
   * A comparison of a Channel with a value in the Rules: the Rule it belongs to (or several, as
   * {@code "Run, QuickRestart"}), the condition, a part of the Rule and a comparison ({@code
   * "when >"}, {@code "endWhen <"}), and the value. The chart draws thresholds of one value on one
   * line.
   */
  record Threshold(Channel channel, String rule, String condition, double value) {}

  /**
   * The time in milliseconds after each Event of {@code after} that the {@code within} of the
   * Rule {@code rule} counts.
   */
  record Window(String rule, String after, long millis) {}

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
