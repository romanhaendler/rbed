package io.github.romanhaendler.rbed;

import static io.github.romanhaendler.rbed.Conditions.after;
import static io.github.romanhaendler.rbed.Conditions.count;
import static io.github.romanhaendler.rbed.Conditions.during;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DescriptionTest {
  static final Channel TEMP = Channel.named("temp");
  static final Channel P = Channel.named("p");
  static final Rule RUN = Rule.named("Run").when(TEMP.above(0));
  static final Rule MAINTENANCE = Rule.named("Maintenance").when(TEMP.below(0));

  @Test
  void aConditionReadsAsItIsWrittenInJava() {
    assertEquals("temp.above(80.0)", TEMP.above(80).toString());
    assertEquals("temp.above(p)", TEMP.above(P).toString());
    assertEquals("temp.below(80.5)", TEMP.below(80.5).toString());
    assertEquals("temp.between(1.0, 2.0)", TEMP.between(1, 2).toString());
    assertEquals("temp.rising()", TEMP.rising().toString());
    assertEquals("temp.averageOverSamples(3).falling()",
        TEMP.averageOverSamples(3).falling().toString());
    assertEquals("temp.above(1.0).and(p.below(2.0)).or(temp.rising()).not()",
        TEMP.above(1).and(P.below(2)).or(TEMP.rising()).not().toString());
    assertEquals("during(Run)", during(RUN).toString());
    assertEquals("after(Run)", after(RUN).toString());
    assertEquals("after(Maintenance).within(7776000000).not()",
        after(MAINTENANCE).within(Duration.ofDays(90)).not().toString());
    assertEquals("after(Run).unless(Maintenance).within(5)",
        after(RUN).within(5).unless(MAINTENANCE).toString());
    assertEquals("count(Run).above(3.0)", count(RUN).above(3).toString());
  }

  @Test
  void aRuleReadsAsItIsWrittenInJava() {
    assertEquals("Rule.named(\"Run\")", Rule.named("Run").toString());
    assertEquals("Rule.named(\"Run\").when(temp.above(0.0))", RUN.toString());
    assertEquals("Rule.named(\"R\").when(temp.above(1.0)).forSamples(3).forDuration(1000)"
            + ".endWhen(temp.below(0.0)).forSamples(2).maxGap(60000).aggregate(temp, p)",
        Rule.named("R").when(TEMP.above(1)).forDuration(1000).forSamples(3)
            .endWhen(TEMP.below(0)).forSamples(2).maxGap(Duration.ofMinutes(1))
            .aggregate(TEMP, P).toString());
  }

  @Test
  void equalConfigurationGivesEqualDescriptions() {
    assertEquals(
        Rule.named("R").when(TEMP.above(1)).aggregate(P, TEMP).toString(),
        Rule.named("R").when(TEMP.above(1)).aggregate(P).aggregate(TEMP).toString());
    assertNotEquals(
        Rule.named("R").when(TEMP.above(1)).aggregate(P, TEMP).toString(),
        Rule.named("R").when(TEMP.above(1)).aggregate(TEMP, P).toString());
    assertNotEquals(
        Rule.named("R").when(TEMP.above(1)).toString(),
        Rule.named("R").when(TEMP.above(2)).toString());
  }
}
