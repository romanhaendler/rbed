package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.during;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MachineToolOverheatExample: a spindle's temperature and load at 1 Hz, pushed without timestamps
 * (sampling-rate mode).
 */
final class Manufacturing extends Scenario {
  static final Channel LOAD = Channel.named("load");
  static final Channel TEMP = Channel.named("temp");

  Manufacturing() {
    super("manufacturing", "Manufacturing", "s", 1_000, List.of(TEMP, LOAD),
        List.of(
            new Parameter("overheat", "Overheat above", 80, 65, 95, 1, "°C"),
            new Parameter("overheatSeconds", "Overheat for", 30, 0, 120, 5, "s"),
            new Parameter("cooled", "Cooled below", 70, 61, 90, 1, "°C"),
            new Parameter("highLoad", "High load above", 80, 50, 85, 5, "%"),
            new Parameter("heating", "Fast heating above", 0.5, 0.25, 2, 0.25, "°C/s")),
        """
        import static io.github.romanhaendler.rbed.Conditions.*;

        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.ArrayList;
        import java.util.List;
        import java.util.Map;

        Channel load = Channel.named("load");
        Channel temp = Channel.named("temp");

        Rule highLoad = Rule.named("HighLoad")
            .when(load.above(${highLoad})).forDuration(Duration.ofSeconds(10))
            .endWhen(load.below(60));
        Rule overheat = Rule.named("Overheat")
            .when(temp.above(${overheat})).forDuration(Duration.ofSeconds(${overheatSeconds}))
            .endWhen(temp.below(${cooled})).forDuration(Duration.ofMinutes(1));
        Rule overheatUnderLoad = Rule.named("OverheatUnderLoad")
            .when(during(overheat).and(during(highLoad)));
        Rule fastHeating = Rule.named("FastHeating").when(temp.rate().above(${heating})).forSamples(5);
        Detector detector = Detector.builder()
            .samplingRate(1)  // 1 Hz: values without timestamps
            .rules(highLoad, overheat, overheatUnderLoad, fastHeating)
            .build();

        List<Map<Channel, Double>> series = new ArrayList<>();
        for (int s = 0; s < 600; s++) series.add(Map.of(temp, spindleTemp(s), load, spindleLoad(s)));
        List<Event> events = detector.detectValues(series);

        // In the enclosing class:
        static double spindleTemp(int s) {   // °C
          if (s < 100) return 60;
          if (s < 140) return 60 + (s - 100);   // heating at 1 °C/s
          if (s < 300) return 100;
          if (s < 335) return 100 - (s - 300);  // cooling at 1 °C/s
          return 65;
        }

        static double spindleLoad(int s) {   // %
          return s < 100 ? 50 : s < 400 ? 90 : 40;
        }
        """);
  }

  static double temperature(double s) {
    if (s < 100) return 60;
    if (s < 140) return 60 + (s - 100);  // 1 °C/s
    if (s < 300) return 100;
    if (s < 335) return 100 - (s - 300); // cooling 1 °C/s
    return 65;
  }

  static double load(double s) {
    return s < 100 ? 50 : s < 400 ? 90 : 40;
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> series = new ArrayList<>();
    for (int s = 0; s < 600; s++) {
      series.add(new Sample(s * 1_000L, Map.of(TEMP, temperature(s), LOAD, load(s))));
    }
    return series;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule highLoad = Rule.named("HighLoad")
        .when(LOAD.above(values.get("highLoad"))).forDuration(10_000)
        .endWhen(LOAD.below(60));
    Rule overheat = Rule.named("Overheat")
        .when(TEMP.above(values.get("overheat")))
        .forDuration(Math.round(values.get("overheatSeconds") * 1000))
        .endWhen(TEMP.below(values.get("cooled"))).forDuration(60_000);
    Rule overheatUnderLoad = Rule.named("OverheatUnderLoad")
        .when(during(overheat).and(during(highLoad)));
    Rule fastHeating = Rule.named("FastHeating")
        .when(TEMP.rate().above(values.get("heating"))).forSamples(5);
    return new Rule[] {highLoad, overheat, overheatUnderLoad, fastHeating};
  }

  /** The Samples' values in order; the Detector derives the timestamps from its sampling rate. */
  @Override
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    Detector detector = Detector.builder().samplingRate(1).rules(rules).listener(lifecycle).build();
    for (Sample sample : samples) lifecycle.pushValues(detector, sample);  // round(i * 1000 / 1)
    detector.close();
  }
}
