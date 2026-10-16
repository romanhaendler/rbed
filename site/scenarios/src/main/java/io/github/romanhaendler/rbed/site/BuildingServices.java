package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.after;
import static io.github.romanhaendler.rbed.Conditions.count;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** HvacShortCyclingExample: an air conditioner's compressor power every 10 s. */
final class BuildingServices extends Scenario {
  static final Channel POWER = Channel.named("power");
  static final long MINUTE = 60_000;

  BuildingServices() {
    super("building-services", "Building services", "s", 1_000, List.of(POWER),
        List.of(
            new Parameter("running", "Running above", 500, 100, 1900, 100, "W"),
            new Parameter("runSeconds", "Running for", 30, 10, 120, 10, "s"),
            new Parameter("restartMinutes", "Quick restart within", 3, 1, 15, 1, "min"),
            new Parameter("starts", "Frequent: more than", 2, 1, 4, 1, "Runs an hour"),
            new Parameter("shortMinutes", "Short cycle under", 5, 1, 15, 1, "min")),
        """
        Channel power = Channel.named("power");
        long SECOND = 1_000, MINUTE = 60 * SECOND;

        Rule run = Rule.named("Run")
            .when(power.above(${running})).forDuration(${runSeconds} * SECOND)
            .endWhen(power.below(100)).forDuration(30 * SECOND);
        Rule quickRestart = Rule.named("QuickRestart")
            .when(after(run).within(${restartMinutes} * MINUTE).and(power.above(${running})))
            .forDuration(30 * SECOND)
            .endWhen(power.below(100));
        Rule frequentStarts = Rule.named("FrequentStarts")
            .when(count(run).within(60 * MINUTE).above(${starts}));
        Detector detector = Detector.builder()
            .rules(run, quickRestart, frequentStarts)
            .build();

        // A plain method classifies the Ended Runs
        boolean shortCycle(Event run) {
          return run.end().getAsLong() - run.start() < ${shortMinutes} * MINUTE;
        }
        """);
  }

  static double power(double s) {
    boolean on = (s >= 100 && s < 700) || (s >= 800 && s < 920) || (s >= 1600 && s < 2200);
    return on ? 2_000 : 20;
  }

  @Override
  List<Sample> samples() {
    List<Sample> series = new ArrayList<>();
    for (int s = 0; s < 2400; s += 10) series.add(new Sample(s * 1_000L, Map.of(POWER, power(s))));
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    double running = values.get("running");
    Rule runRule = Rule.named("Run")
        .when(POWER.above(running)).forDuration(Math.round(values.get("runSeconds") * 1000))
        .endWhen(POWER.below(100)).forDuration(30_000);
    Rule quickRestart = Rule.named("QuickRestart")
        .when(after(runRule).within(Math.round(values.get("restartMinutes") * MINUTE))
            .and(POWER.above(running)))
        .forDuration(30_000)
        .endWhen(POWER.below(100));
    Rule frequentStarts = Rule.named("FrequentStarts")
        .when(count(runRule).within(60 * MINUTE).above(values.get("starts")));
    return new Rule[] {runRule, quickRestart, frequentStarts};
  }

  @Override
  String note(Event event, Map<String, Double> values) {
    boolean shortCycle =
        event.end().getAsLong() - event.start() < values.get("shortMinutes") * MINUTE;
    return event.name().equals("Run") && shortCycle ? "short cycle" : null;
  }
}
