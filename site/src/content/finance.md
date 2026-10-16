# Finance

Daily closing prices fall for 300 days and then rise. A golden cross is the 50-day simple moving average of the price crossing above the 200-day one, a death cross crossing below it.

<!-- scenario finance -->

## The Rules

- **DeathCross**: the fast average below the slow one. It is Truncated at its start: when both averages first exist, on day 199, the 50-day one is already below, so nobody knows when it crossed.
- **GoldenCross**: the fast average above the slow one, from day 366, the day the example also finds by brute force over the two averages. It still holds when the data ends on day 599, so its end is Truncated.

`averageOverSamples(n)` is defined once `n` values exist, so a longer slow average starts the DeathCross later.

## Source and reference

The example is [TradingSignalsExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/TradingSignalsExample.java), listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples) with its expected Events.

Methods used: [`Rule.when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`averageOverSamples`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverSamples%28int%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io/github/romanhaendler/rbed/Expression.html#above%28io.github.romanhaendler.rbed.Expression%29), [`below`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io/github/romanhaendler/rbed/Expression.html#below%28io.github.romanhaendler.rbed.Expression%29), [`Event.truncatedStart`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io/github/romanhaendler/rbed/Event.html#truncatedStart%28%29).
