package io.github.romanhaendler.rbed.site;

import io.github.romanhaendler.rbed.Aggregates;
import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
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
      new EventLifecycle(),
      new EndConditions(),
      new DataGaps(),
      new Expressions(),
      new AggregatesScenario(),
      new PulseChannels(),
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
   * the names of its Pulse Channels, Rule names, parameters (with default and limits) and source template.
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
      out.append("],\"rules\":[");
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
   * @param drawnChannels JSON object reserved for Channel values the reader drew; ignored for now
   * @return JSON object with the Samples, the Events, the Discarded Pending Events (with the
   *     timestamp they were Discarded) and, for a Scenario that plots an Expression, its name,
   *     threshold and value per Sample (null where undefined); or {@code {"error": message}} if
   *     rbed rejected the Rules or the input was invalid
   */
  @JSExport
  public static String run(String scenarioId, String parameters, String drawnChannels) {
    try {
      Scenario scenario = scenario(scenarioId);
      Map<String, Double> values = values(scenario, parameters);
      List<Sample> samples = scenario.samples(values);
      Lifecycle lifecycle = new Lifecycle();
      scenario.detect(scenario.rules(values), samples, lifecycle);
      return result(scenario, values, samples, lifecycle);
    } catch (RuntimeException e) {
      return Json.string(new StringBuilder("{\"error\":"), String.valueOf(e.getMessage()))
          .append('}').toString();
    }
  }

  private static Scenario scenario(String id) {
    for (Scenario s : SCENARIOS) {
      if (s.id.equals(id)) return s;
    }
    throw new IllegalArgumentException("Unknown Scenario " + id);
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
      values.put(p.id(), d);
    }
    return values;
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
      String note = scenario.note(e, values);
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
    out.append(']');
    Scenario.Plot plot = scenario.plot(values);
    if (plot != null) {
      Json.string(out.append(",\"expression\":{\"name\":"), plot.name());
      Json.number(out.append(",\"threshold\":"), plot.threshold()).append(",\"values\":[");
      double[] plotted = ExpressionValues.of(plot.expression(), samples);
      for (int i = 0; i < plotted.length; i++) {
        if (i > 0) out.append(',');
        Json.number(out, plotted[i]);
      }
      out.append("]}");
    }
    return out.append('}').toString();
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
