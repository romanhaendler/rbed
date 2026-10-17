package io.github.romanhaendler.rbed.site;

import static io.github.romanhaendler.rbed.Conditions.count;

import io.github.romanhaendler.rbed.Channel;
import io.github.romanhaendler.rbed.Detector;
import io.github.romanhaendler.rbed.Event;
import io.github.romanhaendler.rbed.EventListener;
import io.github.romanhaendler.rbed.Rule;
import io.github.romanhaendler.rbed.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * The README's example of resuming a stream: a machine's temperature and load, one message a
 * second for two minutes. The process dies after a chosen message, and the stream is resumed
 * naively, with History only, from the Replay Point, or from a Snapshot; the bridge also runs an
 * uninterrupted Detector, whose Events the resumed run is compared with. The Rules are those of
 * the README's example test.
 */
final class Resuming extends Scenario {
  static final Channel TEMP = Channel.named("temp");
  static final Channel LOAD = Channel.named("load");
  static final int MESSAGES = 120;

  Resuming() {
    super("resuming", "Resuming a stream", "s", 1000, List.of(TEMP, LOAD),
        List.of(
            new Parameter("stop", "Stopped after message", 45, 1, MESSAGES - 1, 1, ""),
            Parameter.choice("restart", "Restart", 3, List.of(
                new Option("Naive", """
                    // naive: a fresh Detector that knows nothing
                    Detector restarted = Detector.builder().rules(rules).listener(listener)
                        .build();
                    for (int i = stop; i < topic.size(); i++) {
                      restarted.push(topic.get(i).timestamp(), topic.get(i).values());
                    }
                    restarted.close();
                    """),
                new Option("History only", """
                    // History: the Events that Ended before the stop
                    Detector restarted = Detector.builder().rules(rules)
                        .history(ended).listener(listener).build();
                    for (int i = stop; i < topic.size(); i++) {
                      restarted.push(topic.get(i).timestamp(), topic.get(i).values());
                    }
                    restarted.close();
                    """),
                new Option("Replay Point", """
                    // replay from the Replay Point, with the Events Ended before it as History
                    long from = replayPoint.orElse(Long.MIN_VALUE);
                    List<Event> history = ended.stream()
                        .filter(e -> e.end().getAsLong() < from).toList();
                    Detector restarted = Detector.builder().rules(rules)
                        .history(history).listener(listener).build();
                    int i = 0;
                    while (topic.get(i).timestamp() < from) i++;
                    replaying[0] = true;   // these messages were reported before the stop
                    for (; i < stop; i++) {
                      restarted.push(topic.get(i).timestamp(), topic.get(i).values());
                    }
                    replaying[0] = false;
                    for (; i < topic.size(); i++) {
                      restarted.push(topic.get(i).timestamp(), topic.get(i).values());
                    }
                    restarted.close();
                    """),
                new Option("Snapshot", """
                    // restore the Snapshot and go on with the next message
                    Detector restarted = Detector.builder().rules(rules)
                        .restore(snapshot).listener(listener).build();
                    for (int i = stop; i < topic.size(); i++) {
                      restarted.push(topic.get(i).timestamp(), topic.get(i).values());
                    }
                    restarted.close();
                    """)))),
        """
        import static io.github.romanhaendler.rbed.Conditions.*;

        import io.github.romanhaendler.rbed.*;
        import java.time.Duration;
        import java.util.ArrayList;
        import java.util.List;
        import java.util.Map;
        import java.util.OptionalLong;

        Channel temp = Channel.named("temp");
        Channel load = Channel.named("load");
        Rule overheat = Rule.named("Overheat").when(temp.above(80))
            .forDuration(Duration.ofSeconds(3)).endWhen(load.below(10));
        Rule warm = Rule.named("Warm")
            .when(temp.averageOverSamples(5).above(60)).forSamples(3);
        Rule repeated = Rule.named("Repeated")
            .when(count(overheat).within(Duration.ofSeconds(60)).above(1));
        Rule[] rules = {overheat, warm, repeated};

        List<Sample> topic = new ArrayList<>();   // the stream: one message a second
        for (int s = 0; s < 120; s++) {
          topic.add(new Sample(s * 1_000L, Map.of(temp, temperature(s), load, loadAt(s))));
        }

        int stop = ${stop};   // the process dies after this many messages
        List<Event> ended = new ArrayList<>();   // stored with each message
        boolean[] replaying = {false};
        EventListener listener = new EventListener() {
          @Override public void onEnded(Event e) {
            if (!replaying[0]) ended.add(e);
          }
        };
        Detector detector = Detector.builder().rules(rules).listener(listener).build();
        for (int i = 0; i < stop; i++) {
          detector.push(topic.get(i).timestamp(), topic.get(i).values());
        }
        OptionalLong replayPoint = detector.replayPoint();   // stored with each message
        byte[] snapshot = detector.snapshot();               // stored every so often
        // the process dies here: detector is never closed

        ${restart}
        // In the enclosing class:
        static double temperature(int s) {
          return s < 10 ? 40 : s < 30 ? 90 : s < 40 ? 50 : s < 70 ? 85 : s < 90 ? 45 : s < 100 ? 88 : 45;
        }

        static double loadAt(int s) {
          return (s >= 20 && s < 35) || (s >= 60 && s < 80) || s >= 100 ? 5 : 50;
        }
        """);
  }

  static double temperature(int s) {
    return s < 10 ? 40 : s < 30 ? 90 : s < 40 ? 50 : s < 70 ? 85 : s < 90 ? 45 : s < 100 ? 88 : 45;
  }

  static double load(int s) {
    return (s >= 20 && s < 35) || (s >= 60 && s < 80) || s >= 100 ? 5 : 50;
  }

  @Override
  List<Sample> samples(Map<String, Double> values) {
    List<Sample> samples = new ArrayList<>();
    for (int s = 0; s < MESSAGES; s++) {
      samples.add(new Sample(s * 1000L, Map.of(TEMP, temperature(s), LOAD, load(s))));
    }
    return samples;
  }

  @Override
  Rule[] rules(Map<String, Double> values) {
    Rule overheat = Rule.named("Overheat").when(TEMP.above(80)).forDuration(3_000)
        .endWhen(LOAD.below(10));
    Rule warm = Rule.named("Warm").when(TEMP.averageOverSamples(5).above(60)).forSamples(3);
    Rule repeated = Rule.named("Repeated").when(count(overheat).within(60_000).above(1));
    return new Rule[] {overheat, warm, repeated};
  }

  /**
   * Runs an uninterrupted Detector, then the stream up to the stop in another, which is not
   * closed (the process dies), then the restart the reader chose from the next message on.
   */
  @Override
  void detect(Rule[] rules, List<Sample> samples, Map<String, Double> values,
      Lifecycle lifecycle) {
    int stop = (int) Math.round(values.get("stop"));
    int restart = (int) Math.round(values.get("restart"));

    List<Event> uninterrupted = new ArrayList<>();
    Detector whole = Detector.builder().rules(rules).listener(new EventListener() {
      @Override
      public void onEnded(Event event) {
        uninterrupted.add(event);
      }
    }).build();
    for (Sample sample : samples) whole.push(sample.timestamp(), sample.values());
    whole.close();

    Detector first = lifecycle.builder().rules(rules).build();
    for (int i = 0; i < stop; i++) lifecycle.push(first, samples.get(i));
    OptionalLong replayPoint = first.replayPoint();
    byte[] snapshot = first.snapshot();
    lifecycle.snapshot = snapshot;
    lifecycle.resume = new Lifecycle.Resume(samples.get(stop - 1).timestamp(), replayPoint,
        first.memoryHorizon(), uninterrupted);
    // the process dies here: first is not closed

    Detector.Builder builder = lifecycle.builder().rules(rules);
    int from = stop;
    if (restart == 1) {
      builder.history(List.copyOf(lifecycle.ended));
    } else if (restart == 2) {
      long point = replayPoint.orElse(Long.MIN_VALUE);
      builder.history(lifecycle.ended.stream().filter(e -> e.end().getAsLong() < point).toList());
      from = 0;
      while (samples.get(from).timestamp() < point) from++;
    } else if (restart == 3) {
      builder.restore(lifecycle.given != null ? lifecycle.given : snapshot);
    }
    Detector restarted = builder.build();
    lifecycle.replaying = true;   // the messages between the Replay Point and the stop were reported
    for (; from < stop; from++) {
      restarted.push(samples.get(from).timestamp(), samples.get(from).values());
    }
    lifecycle.replaying = false;
    for (int i = stop; i < samples.size(); i++) lifecycle.push(restarted, samples.get(i));
    restarted.close();
  }

  @Override
  List<Threshold> thresholds(Map<String, Double> values) {
    return List.of(new Threshold(TEMP, "Overheat", "when >", 80),
        new Threshold(LOAD, "Overheat", "endWhen <", 10));
  }
}
