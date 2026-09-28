package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionVocabularyTest {
  static final Channel LIMIT = Channel.named("limit");
  static final Channel MISSING = Channel.named("missing");
  static final Channel WEIGHT = Channel.named("weight");
  static final Channel X = Channel.named("x");

  final Recorder rec = new Recorder();

  /** Pushes one single-channel Sample per value at timestamps 0, 1, 2, ... */
  List<String> run(Condition c, double... xs) {
    Detector d = Detector.builder().rules(Rule.named("E").when(c)).listener(rec).build();
    for (int i = 0; i < xs.length; i++) d.push(i, Map.of(X, xs[i]));
    d.close();
    return rec.endedLines();
  }

  @Test
  void below() {
    assertEquals(List.of("ended E 1..3"), run(X.below(5), 9, 1, 2, 7));
  }

  @Test
  void betweenIsInclusive() {
    assertEquals(List.of("ended E 1..4"), run(X.between(2, 4), 1, 2, 3, 4, 5));
  }

  @Test
  void aboveIsStrict() {
    assertEquals(List.of("ended E 2..3"), run(X.above(5), 4, 5, 6, 5));
  }

  @Test
  void andOrNot() {
    Condition band = X.above(2).and(X.below(6));
    assertEquals(List.of("ended E 1..3"), run(band, 1, 3, 5, 7));
    rec.log.clear();
    Condition outside = X.below(2).or(X.above(6));
    assertEquals(List.of("ended E 0..1 truncatedStart", "ended E 3..4 truncatedEnd"), run(outside, 1, 3, 5, 7, 8));
    rec.log.clear();
    assertEquals(List.of("ended E 1..3"), run(band.not(), 3, 7, 8, 3));
  }

  @Test
  void channelAgainstChannel() {
    Detector d = Detector.builder()
        .rules(Rule.named("Overload").when(WEIGHT.above(LIMIT)))
        .listener(rec).build();
    d.push(0, Map.of(WEIGHT, 100.0, LIMIT, 200.0));
    d.push(1, Map.of(WEIGHT, 300.0));        // limit held at 200
    d.push(2, Map.of(LIMIT, 400.0));         // weight held at 300
    assertEquals(List.of("pending Overload 1", "confirmed Overload 1", "ended Overload 1..2"),
        rec.log);
  }

  @Test
  void unknownChannelIsFalseEvenWhenNegated() {
    Detector d = Detector.builder()
        .rules(Rule.named("A").when(MISSING.above(0)))
        .rules(Rule.named("B").when(MISSING.above(0).not()))
        .rules(Rule.named("C").when(MISSING.above(0).or(X.above(0))))
        .listener(rec).build();
    d.push(0, Map.of(X, 1.0));
    assertEquals(List.of("pending C 0", "confirmed C 0"), rec.log);
  }
}
