# Environment

A river gauge reports the water level every 15 minutes; the report at 20 h is missing. Here the level rises 20 cm/h to a crest after 12 h and then falls 10 cm/h. The rate is taken of the average of the last 4 reports, so a noisy gauge would not make the warning flicker.

<!-- scenario environment -->

## The Rules

- **FloodWarning**: above 300 cm and still rising faster than 10 cm/h, for 30 minutes; lifted only once below 250 cm for 2 hours. Reports more than 2 hours apart interrupt it. It starts at 5.25 h (305 cm) and ends at 31.25 h (247.5 cm).
- **Receding**: falling while still above 300 cm, from 12.25 h (437.5 cm) to 26 h, where the level is exactly 300 cm.

### Things to try

- Set *Interrupted by a gap over* to 15 min, below the 30 minutes between the reports around 20 h: the missing report interrupts the warning, which Ends Truncated at 19.75 h.

## Reference

The example is [FloodWarningExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/FloodWarningExample.java), a test with its expected Events, listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples).

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`ChannelExpression.averageOverSamples`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverSamples%28int%29), [`ChannelExpression.rate`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#rate%28%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Rule.endWhen`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`Expression.below`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`Rule.maxGap`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#maxGap%28java.time.Duration%29), [`ChannelExpression.falling`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#falling%28%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).

The concepts at work, each on a page of its own: [Conditions and Expressions](../conditions-and-expressions/), [End Conditions and hysteresis](../end-conditions/), [Data gaps](../data-gaps/).
