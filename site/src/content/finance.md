# Finance

Daily closing prices fall for 300 days and then rise. A golden cross is the 50-day simple moving average of the price crossing above the 200-day one, a death cross crossing below it.

<!-- scenario finance -->

## The Rules

- **DeathCross**: the fast average below the slow one.
- **GoldenCross**: the fast average above the slow one.

`averageOverSamples(n)` is defined once `n` values exist, so the first Event starts once the longer of the two averages exists: on day 199 here, later with a longer one.

At the defaults, `DeathCross` runs from day 199 to day 366. It is Truncated at its start: when both averages first exist, on day 199, the 50-day one is already below, so nobody knows when it crossed. `GoldenCross` follows from day 366, the day the example also finds by brute force over the two averages. It still holds when the data ends on day 599, so its end is Truncated.

### Things to try

- Raise *Slow average over* to 300 Samples: the slow average first exists on day 299, so `DeathCross` starts there, Truncated, and `GoldenCross` follows on day 388.

## Reference

The example is [TradingSignalsExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/TradingSignalsExample.java), a test with its expected Events, listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples).

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`ChannelExpression.averageOverSamples`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverSamples%28int%29), [`Expression.below`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28io.github.romanhaendler.rbed.Expression%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28io.github.romanhaendler.rbed.Expression%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).

The concepts at work, each on a page of its own: [Conditions and Expressions](../conditions-and-expressions/), [The Event lifecycle](../event-lifecycle/).
