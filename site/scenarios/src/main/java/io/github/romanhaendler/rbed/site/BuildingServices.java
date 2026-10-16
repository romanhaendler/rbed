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
        import static io.github.romanhaendler.rbed.Conditions.*;

        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.Map;

        Channel power = Channel.named("power");

        Rule run = Rule.named("Run")
            .when(power.above(${running})).forDuration(Duration.ofSeconds(${runSeconds}))
            .endWhen(power.below(100)).forDuration(Duration.ofSeconds(30));
        Rule quickRestart = Rule.named("QuickRestart")
            .when(after(run).within(Duration.ofMinutes(${restartMinutes})).and(power.above(${running})))
            .forDuration(Duration.ofSeconds(30))
            .endWhen(power.below(100));
        Rule frequentStarts = Rule.named("FrequentStarts")
            .when(count(run).within(Duration.ofHours(1)).above(${starts}));
        Detector detector = Detector.builder()
            .rules(run, quickRestart, frequentStarts)
            .listener(new EventListener() {
              @Override public void onEnded(Event e) {
                if (e.is(run) && shortCycle(e)) System.out.println("short cycle from " + e.start());
              }
            })
            .build();

        for (int s = 0; s < 2_400; s += 10) {   // the power every 10 s
          boolean on = s >= 100 && s < 700 || s >= 800 && s < 920 || s >= 1_600 && s < 2_200;
          detector.push(s * 1_000L, Map.of(power, on ? 2_000.0 : 20.0));
        }
        detector.close();

        // In the enclosing class:
        // A plain method classifies the Ended Runs
        static boolean shortCycle(Event run) {
          return run.end().getAsLong() - run.start() < Duration.ofMinutes(${shortMinutes}).toMillis();
        }
        """);
  }

  static double power(double s) {
    boolean on = (s >= 100 && s < 700) || (s >= 800 && s < 920) || (s >= 1600 && s < 2200);
    return on ? 2_000 : 20;
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
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

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(POWER, "Run, QuickRestart", "when >", values.get("running")),
        new Threshold(POWER, "Run, QuickRestart", "endWhen <", 100));
  }

  @Override
  List<Window> windows(Map<String, Double> values) {
    return List.of(new Window("QuickRestart", "Run", Math.round(values.get("restartMinutes") * MINUTE)));
  }
}
