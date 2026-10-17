package io.github.romanhaendler.rbed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The canonical key tells apart everything the readable text can confuse, and nothing more. */
class KeyTest {
  static final Channel X = Channel.named("x");
  static final Channel Y = Channel.named("y");
  static final Rule P = Rule.named("P").when(X.above(1));
  static final Rule Q = Rule.named("Q").when(X.above(1));

  private static void allDifferent(List<String> keys) {
    for (int i = 0; i < keys.size(); i++) {
      for (int j = i + 1; j < keys.size(); j++) {
        assertNotEquals(keys.get(i), keys.get(j), i + "/" + j);
      }
    }
  }

  @Test
  void everyParameterOfEveryConstructIsPartOfTheKey() {
    allDifferent(List.of(
        X.averageOverSamples(3).key(), X.averageOverSamples(4).key(),
        Y.averageOverSamples(3).key(), X.averageOverDuration(3).key(),
        X.averageOverDuration(4).key(), Y.averageOverDuration(3).key(), X.rate().key(),
        Y.rate().key(), X.age().key(), X.rising().key(), X.falling().key(), Y.rising().key(),
        X.above(1).key(), X.below(1).key(), X.above(2).key(), X.above(-0.0).key(),
        X.above(0.0).key(), X.between(1, 2).key(), X.between(1, 3).key(), X.between(0, 2).key(),
        X.plus(1).key(), X.minus(1).key(), X.times(1).key(), X.dividedBy(1).key(),
        X.plus(2).key(), X.abs().key(), X.plus(Y).key(), X.plus(X).key(), Y.plus(X).key(),
        X.above(Y).key(), Y.above(X).key(), X.above(1).and(X.below(2)).key(),
        X.above(1).or(X.below(2)).key(), X.above(1).not().key(), X.below(2).and(X.above(1)).key(),
        Conditions.during(P).key(), Conditions.during(Q).key(), Conditions.after(P).key(),
        Conditions.after(Q).key(), Conditions.after(P).within(5).key(),
        Conditions.after(P).within(6).key(), Conditions.after(P).unless(Q).key(),
        Conditions.after(P).unless(Q, P).key(), Conditions.after(P).unless(P, Q).key(),
        Conditions.count(P).key(), Conditions.count(Q).key(),
        Conditions.count(P).within(5).key(), Conditions.count(P).within(6).key(),
        Conditions.totalDuration(P).key(), Conditions.totalDuration(Q).key(),
        Conditions.latest(P).duration().key(), Conditions.latest(Q).duration().key(),
        Conditions.latest(P).min(X).key(), Conditions.latest(P).max(X).key(),
        Conditions.latest(P).mean(X).key(), Conditions.latest(P).min(Y).key(),
        Conditions.latest(Q).min(X).key(), Channel.pulse("x").key(), Channel.named("y").key()));
  }

  @Test
  void theSameConstructionGivesTheSameKey() {
    assertEquals(X.averageOverSamples(3).above(1).key(), X.averageOverSamples(3).above(1.0).key());
    assertEquals(Rule.named("R").when(X.above(1)).forSamples(2).key(),
        Rule.named("R").when(X.above(1)).forSamples(2).key());
  }

  @Test
  void everyPartOfARuleIsPartOfTheKey() {
    Rule base = Rule.named("R").when(X.above(1));
    allDifferent(List.of(base.key(), Rule.named("S").when(X.above(1)).key(),
        base.forSamples(2).key(), base.forSamples(3).key(), base.forDuration(5).key(),
        base.forDuration(6).key(), base.endWhen(X.below(0)).key(), base.endWhen(X.below(1)).key(),
        base.endWhen(X.below(0)).forSamples(2).key(),
        base.endWhen(X.below(0)).forDuration(2).key(), base.maxGap(5).key(), base.maxGap(6).key(),
        base.aggregate(X).key(), base.aggregate(Y).key(), base.aggregate(X, Y).key(),
        base.aggregate(Y, X).key(), base.aggregate(Channel.named("x, y")).key()));
  }

  @Test
  void namesCannotImitateStructure() {
    allDifferent(List.of(Channel.named("x.abs()").key(), X.abs().key(),
        Channel.named("x.abs").key(), Channel.named("channel(1:x)").key(),
        Channel.named("1:x").key(), X.key()));
    allDifferent(List.of(Rule.named("A, B").key(), Rule.named("A").key(),
        Rule.named("A\")").key()));
  }
}
