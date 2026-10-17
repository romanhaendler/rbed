package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Aggregates;
import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSExport;

/**
 * The only seam between the Scenarios and the web app: the two functions TeaVM exports to
 * JavaScript. Data crosses as JSON text. Keep {@code @JSExport} on this class alone; TeaVM
 * exports every reachable class that has such methods.
 */
public final class Bridge {
  private static final List<Scenario> SCENARIOS = List.of(
      new FirstRule(),
      new SamplesAndChannels(),
      new EventLifecycle(),
      new EndConditions(),
      new DataGaps(),
      new Expressions(),
      new AggregatesScenario(),
      new SamplingRate(),
      new SilentSensors(),
      new PulseChannels(),
      new During(),
      new AfterScenario(),
      new Count(),
      new TotalDuration(),
      new Latest(),
      new History(),
      new Validation(),
      new Cranes(),
      new Manufacturing(),
      new BuildingServices(),
      new Healthcare(),
      new Automotive(),
      new Finance(),
      new Logistics(),
      new Environment(),
      new Sports(),
      new ItOperations());

  private Bridge() {}

  /**
   * The catalogue: a JSON array with, for each Scenario, its id, title, time unit, Channel names,
   * the names of its Pulse Channels, the Rule whose latest Event its Rules read (if any), Rule
   * names, parameters (with default and limits) and source template.
   */
  @JSExport
  public static String scenarios() {
    StringBuilder out = new StringBuilder("[");
    for (Scenario s : SCENARIOS) {
      if (out.length() > 1) out.append(',');
      Json.string(out.append("{\"id\":"), s.id);
      Json.string(out.append(",\"title\":"), s.title);
      Json.string(out.append(",\"timeUnit\":{\"name\":"), s.timeUnit)
          .append(",\"millis\":").append(s.timeUnitMillis).append('}');
      out.append(",\"channels\":[");
      for (int i = 0; i < s.channels.size(); i++) {
        if (i > 0) out.append(',');
        Json.string(out, s.channels.get(i).name());
      }
      out.append("],\"pulse\":[");
      for (int i = 0; i < s.pulseChannels().size(); i++) {
        if (i > 0) out.append(',');
        Json.string(out, s.pulseChannels().get(i).name());
      }
      out.append(']');
      if (s.readsLatest() != null) Json.string(out.append(",\"latest\":"), s.readsLatest());
      out.append(",\"rules\":[");
      Rule[] rules = s.rules(values(s, "{}"));
      for (int i = 0; i < rules.length; i++) {
        if (i > 0) out.append(',');
        Json.string(out, rules[i].name());
      }
      out.append("],\"parameters\":[");
      for (int i = 0; i < s.parameters.size(); i++) {
        Scenario.Parameter p = s.parameters.get(i);
        if (i > 0) out.append(',');
        Json.string(out.append("{\"id\":"), p.id());
        Json.string(out.append(",\"label\":"), p.label());
        Json.number(out.append(",\"default\":"), p.defaultValue());
        Json.number(out.append(",\"min\":"), p.min());
        Json.number(out.append(",\"max\":"), p.max());
        Json.number(out.append(",\"step\":"), p.step());
        Json.string(out.append(",\"unit\":"), p.unit());
        if (!p.options().isEmpty()) {
          out.append(",\"options\":[");
          for (int j = 0; j < p.options().size(); j++) {
            if (j > 0) out.append(',');
            Json.string(out.append("{\"label\":"), p.options().get(j).label());
            Json.string(out.append(",\"source\":"), p.options().get(j).source()).append('}');
          }
          out.append(']');
        }
        out.append('}');
      }
      Json.string(out.append("],\"source\":"), s.source).append('}');
    }
    return out.append(']').toString();
  }

  /**
   * Runs a Scenario's Rules over its Samples.
   *
   * @param parameters JSON object of parameter values by id; missing ones take their default,
   *     null stands for NaN
   * @param drawnChannels JSON object of Channel values the reader drew, by Channel name, one
   *     number per Sample of the Scenario (null: absent in that Sample); they replace that
   *     Channel's own values
   * @return JSON object with the Samples, the Events (in the order they Ended, as rbed reports
   *     them), the Discarded Pending Events (with the timestamp they were Discarded), the
   *     timestamps time was advanced to without a Sample, the timestamp of the restart if the
   *     Detector was restarted (and {@code "restartForgets": true} if it was restarted without
   *     History), each Expression the Scenario plots (its name, the Rule and comparison of its
   *     threshold if it has one, unit if it has one of its own, threshold or null, value per
   *     Sample and, if time was advanced, value per advance; null where undefined), the Channel
   *     thresholds of its Rules and the windows their {@code within} opens; or {@code {"error":
   *     message, "by": "rbed"}} if rbed rejected the Rules or the Samples, or {@code {"error":
   *     message}} if the input was invalid
   */
  @JSExport
  public static String run(String scenarioId, String parameters, String drawnChannels) {
    Scenario scenario;
    try {
      scenario = scenario(scenarioId);
    } catch (RuntimeException e) {
      return error(e, "");
    }
    return run(scenario, parameters, drawnChannels);
  }

  /** {@link #run(String, String, String)} for a Scenario given, not looked up. */
  static String run(Scenario scenario, String parameters, String drawnChannels) {
    Map<String, Double> values;
    List<Sample> samples;
    try {
      values = values(scenario, parameters);
      samples = drawn(scenario, scenario.samples(values), drawnChannels);
    } catch (RuntimeException e) {
      return error(e, "");
    }
    Lifecycle lifecycle = new Lifecycle();
    try {
      scenario.detect(scenario.rules(values), samples, values, lifecycle);
    } catch (IllegalArgumentException | IllegalStateException e) {
      // how rbed rejects Rules and Samples; anything else is a fault of the Scenario's code
      return error(e, ",\"by\":\"rbed\"");
    } catch (RuntimeException e) {
      return error(e, "");
    }
    try {
      return result(scenario, values, samples, lifecycle);
    } catch (RuntimeException e) {
      return error(e, "");
    }
  }

  private static String error(RuntimeException e, String by) {
    return Json.string(new StringBuilder("{\"error\":"), String.valueOf(e.getMessage()))
        .append(by).append('}').toString();
  }

  private static Scenario scenario(String id) {
    for (Scenario s : SCENARIOS) {
      if (s.id.equals(id)) return s;
    }
    throw new IllegalArgumentException("Unknown Scenario " + id);
  }

  /** The Scenario's Samples, with each drawn Channel's values in place of its own. */
  private static List<Sample> drawn(Scenario scenario, List<Sample> samples, String drawnChannels) {
    if (!(Json.parse(drawnChannels) instanceof Map<?, ?> drawn)) {
      throw new IllegalArgumentException("Drawn Channels are not a JSON object");
    }
    if (drawn.isEmpty()) return samples;
    List<Map<Channel, Double>> values = new ArrayList<>();
    for (Sample sample : samples) values.add(new HashMap<>(sample.values()));
    for (Map.Entry<?, ?> entry : drawn.entrySet()) {
      Channel channel = channel(scenario, (String) entry.getKey());
      List<?> drawnValues = entry.getValue() instanceof List<?> l ? l : List.of();
      if (drawnValues.size() != samples.size()) {
        throw new IllegalArgumentException("Drawn Channel " + channel.name() + " has "
            + drawnValues.size() + " values for " + samples.size() + " Samples");
      }
      for (int i = 0; i < samples.size(); i++) {
        Object value = drawnValues.get(i);
        if (value == null) {
          values.get(i).remove(channel);
        } else if (value instanceof Double d) {
          values.get(i).put(channel, d);
        } else {
          throw new IllegalArgumentException(
              "Drawn Channel " + channel.name() + " has a value that is not a number");
        }
      }
    }
    List<Sample> result = new ArrayList<>();
    for (int i = 0; i < samples.size(); i++) {
      result.add(new Sample(samples.get(i).timestamp(), values.get(i)));
    }
    return result;
  }

  private static Channel channel(Scenario scenario, String name) {
    for (Channel c : scenario.channels) {
      if (c.name().equals(name)) return c;
    }
    throw new IllegalArgumentException("No Channel " + name + " in Scenario " + scenario.id);
  }

  private static Map<String, Double> values(Scenario scenario, String parameters) {
    Object parsed = Json.parse(parameters);
    if (!(parsed instanceof Map<?, ?> given)) {
      throw new IllegalArgumentException("Parameters are not a JSON object");
    }
    Map<String, Double> values = new HashMap<>();
    for (Scenario.Parameter p : scenario.parameters) {
      Object value = given.containsKey(p.id()) ? given.get(p.id()) : p.defaultValue();
      if (value == null) value = Double.NaN;
      if (!(value instanceof Double d)) {
        throw new IllegalArgumentException("Parameter " + p.id() + " is not a number");
      }
      if (!p.options().isEmpty() && !(d >= 0 && d < p.options().size() && d == Math.rint(d))) {
        throw new IllegalArgumentException("Parameter " + p.id() + " has no option " + d);
      }
      // Also keeps NaN from the Scenarios: TeaVM's Math.round throws on it, the JVM's gives 0.
      if (!(d >= p.min() && d <= p.max())) {
        throw new IllegalArgumentException("Parameter " + p.id() + " is not a number between "
            + plain(p.min()) + " and " + plain(p.max()));
      }
      values.put(p.id(), d);
    }
    return values;
  }

  /** A whole number without its ".0". */
  private static String plain(double d) {
    return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
  }

  private static String result(Scenario scenario, Map<String, Double> values,
      List<Sample> samples, Lifecycle lifecycle) {
    List<Event> events = lifecycle.ended;
    StringBuilder out = new StringBuilder("{\"samples\":[");
    for (int i = 0; i < samples.size(); i++) {
      Sample sample = samples.get(i);
      if (i > 0) out.append(',');
      out.append("{\"timestamp\":").append(sample.timestamp()).append(",\"values\":{");
      boolean first = true;
      for (Channel channel : scenario.channels) {
        Double value = sample.values().get(channel);
        if (value == null) continue;
        if (!first) out.append(',');
        first = false;
        Json.number(Json.string(out, channel.name()).append(':'), value);
      }
      out.append("}}");
    }
    out.append("],\"events\":[");
    for (int i = 0; i < events.size(); i++) {
      Event e = events.get(i);
      if (i > 0) out.append(',');
      Json.string(out.append("{\"name\":"), e.name())
          .append(",\"start\":").append(e.start())
          .append(",\"confirmed\":").append(e.confirmed().getAsLong())
          .append(",\"end\":").append(e.end().getAsLong())
          .append(",\"truncatedStart\":").append(e.truncatedStart())
          .append(",\"truncatedEnd\":").append(e.truncatedEnd());
      aggregates(out.append(",\"aggregates\":"), scenario, e);
      String note = lifecycle.noteOf(e);
      if (note == null) note = scenario.note(e, values);
      if (note != null) Json.string(out.append(",\"note\":"), note);
      out.append('}');
    }
    out.append("],\"discarded\":[");
    for (int i = 0; i < lifecycle.discarded.size(); i++) {
      Lifecycle.Discard d = lifecycle.discarded.get(i);
      if (i > 0) out.append(',');
      Json.string(out.append("{\"name\":"), d.event().name())
          .append(",\"start\":").append(d.event().start())
          .append(",\"discarded\":").append(d.at())
          .append('}');
    }
    out.append("],\"advances\":[");
    for (int i = 0; i < lifecycle.advances.size(); i++) {
      if (i > 0) out.append(',');
      out.append(lifecycle.advances.get(i));
    }
    out.append(']');
    if (lifecycle.restart != null) out.append(",\"restart\":").append(lifecycle.restart);
    if (lifecycle.restartForgets) out.append(",\"restartForgets\":true");
    out.append(",\"expressions\":[");
    List<Scenario.Plot> plots = scenario.plots(values);
    for (int i = 0; i < plots.size(); i++) {
      Scenario.Plot plot = plots.get(i);
      if (i > 0) out.append(',');
      Json.string(out.append("{\"name\":"), plot.name());
      if (plot.rule() != null) Json.string(out.append(",\"rule\":"), plot.rule());
      if (plot.condition() != null) Json.string(out.append(",\"condition\":"), plot.condition());
      if (plot.unit() != null) Json.string(out.append(",\"unit\":"), plot.unit());
      ExpressionValues.Values plotted =
          ExpressionValues.of(scenario, plot.expression(), values, samples);
      Json.number(out.append(",\"threshold\":"), plot.threshold());
      numbers(out.append(",\"values\":"), plotted.atSamples());
      if (plotted.atAdvances().length > 0) {
        numbers(out.append(",\"atAdvances\":"), plotted.atAdvances());
      }
      out.append('}');
    }
    out.append("],\"thresholds\":[");
    List<Scenario.Threshold> thresholds = scenario.thresholds(values);
    for (int i = 0; i < thresholds.size(); i++) {
      Scenario.Threshold t = thresholds.get(i);
      if (i > 0) out.append(',');
      Json.string(out.append("{\"channel\":"), t.channel().name());
      Json.string(out.append(",\"rule\":"), t.rule());
      Json.string(out.append(",\"condition\":"), t.condition());
      Json.number(out.append(",\"value\":"), t.value()).append('}');
    }
    out.append("],\"windows\":[");
    List<Scenario.Window> windows = scenario.windows(values);
    for (int i = 0; i < windows.size(); i++) {
      if (i > 0) out.append(',');
      Json.string(out.append("{\"rule\":"), windows.get(i).rule());
      Json.string(out.append(",\"after\":"), windows.get(i).after())
          .append(",\"millis\":").append(windows.get(i).millis()).append('}');
    }
    out.append(']');
    return out.append('}').toString();
  }

  /** A JSON array of the numbers; NaN becomes null. */
  private static StringBuilder numbers(StringBuilder out, double[] numbers) {
    out.append('[');
    for (int i = 0; i < numbers.length; i++) {
      if (i > 0) out.append(',');
      Json.number(out, numbers[i]);
    }
    return out.append(']');
  }

  /** The Event's Aggregates by Channel name, in the Scenario's Channel order; NaN becomes null. */
  private static StringBuilder aggregates(StringBuilder out, Scenario scenario, Event e) {
    out.append('{');
    boolean first = true;
    for (Channel channel : scenario.channels) {
      Aggregates a = e.aggregates().get(channel);
      if (a == null) continue;
      if (!first) out.append(',');
      first = false;
      Json.string(out, channel.name()).append(':');
      Json.number(out.append("{\"min\":"), a.min());
      Json.number(out.append(",\"max\":"), a.max());
      Json.number(out.append(",\"mean\":"), a.mean());
      out.append(",\"count\":").append(a.count());
      Json.number(out.append(",\"timeWeightedMean\":"), a.timeWeightedMean());
      Json.number(out.append(",\"integral\":"), a.integral()).append('}');
    }
    return out.append('}');
  }
}
