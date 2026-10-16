# Errors and validation

rbed reports a mistake as early as it can, most of them when the Detector is built, and always with an exception whose message names what is wrong. On this page, some controls lead into such mistakes on purpose: rbed then rejects the Rules, and the page shows its message in place of the chart.

## A warm room

A room's temperature is reported once a second; it is 28 °C from 10 to 29 s and 22 °C otherwise. `Warm` holds while the average of the last few Samples is above 25 °C, for a duration; `Hot` holds while a `Warm` Event runs and the temperature itself is above 27 °C.

<!-- scenario validation -->

At the defaults, the average over 5 Samples rises above 25 °C at 12 s, the third warm Sample, and `Warm` is Confirmed two seconds later; `Hot` starts with that confirmation and Ends at 30 s, the first cool Sample.

## Things to try

- Raise *Average over* above the *Retention Limit*. A window over Samples keeps that many values, which the Retention Limit caps, so `build()` rejects the Rule and says how to fix it: raise the limit with `Detector.builder().retentionLimit(...)`.
- Set the *Retention Limit* to 0: `retentionLimit` itself rejects a limit below one.
- Set *Warm for* to −1 s: `forDuration` rejects a negative duration as soon as the Rule is written, before any Detector exists.
- Add `Hot` before `Warm`. A Rule may only refer to Rules added before it, since Rules are evaluated in the order they are added, so `build()` rejects `Hot`.

## What else is rejected

- An empty Rule or Channel name, a Rule without `when`, two Rules with the same name.
- `forDuration` or `forSamples` before any `when` or `endWhen`; negative durations, `forSamples(0)`, `between` with its low above its high.
- A sampling rate outside (0, 1000] Hz; a History Event that has not Ended, or whose name matches no Rule; one Channel name used both as a Pulse Channel and as an ordinary one.
- While running: a timestamp not after the previous one, a NaN, infinite or null value (the Sample is then ignored), pushing after `close()`, or the wrong `push` for the sampling mode.
- Reading the Aggregates of a Channel the Rule does not aggregate.

The [Restarts: History](../history/) page restarts a Detector, and the [Silent sensors](../silent-sensors/) page advances time, both within these rules.

## Reference

- [Errors and validation](https://github.com/romanhaendler/rbed#errors-and-validation) in the README, the canonical text, with the method that reports each mistake

Methods used: [`when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`averageOverSamples`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverSamples%28int%29), [`forDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`during`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#during%28io.github.romanhaendler.rbed.Rule%29), [`and`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`retentionLimit`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#retentionLimit%28int%29), [`rules`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`build`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29).
