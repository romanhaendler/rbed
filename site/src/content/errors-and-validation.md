# Errors and validation

rbed reports a mistake as early as it can, most of them when the Detector is built, and always with an exception whose message names what is wrong.

<!-- live -->
On this page, some controls lead into such mistakes on purpose: rbed then rejects the Rules, and the page shows its message in place of the chart.

## A warm room

A room's temperature is reported once a second; it is 28 °C from 10 to 29 s and 22 °C otherwise. `Warm` holds while the average of the last few Samples is above 25 °C, for a duration; `Hot` holds while a `Warm` Event runs and the temperature itself is above 27 °C.

<!-- scenario validation -->

At the defaults, the average over 5 Samples rises above 25 °C at 12 s, the third warm Sample, and `Warm` is Confirmed two seconds later; `Hot` starts with that confirmation and Ends at 30 s, the first cool Sample.

### Things to try

- Raise *Average over* above the *Retention Limit*, the most entries one window may keep (1,000,000 by default, 10 here). An average over n Samples must keep n values, so `build()` rejects an n above the Retention Limit and says how to fix it: raise the limit with `Detector.builder().retentionLimit(...)`.
- Set the *Retention Limit* to 0: `retentionLimit` itself rejects a limit below one.
- Set *Warm for* to −1 s: `forDuration` rejects a negative duration as soon as the Rule is written, before any Detector exists.
- Add `Hot` before `Warm`. A Rule may only refer to Rules added before it, since Rules are evaluated in the order they are added, so `build()` rejects `Hot`.

## What else is rejected

- An empty Rule or Channel name, a Rule without `when`, two Rules with the same name.
- `forDuration` or [`forSamples`](../event-lifecycle/#durations-forduration-and-forsamples) before any `when` or `endWhen`; negative durations, `forSamples(0)`, `between` with its low above its high.
- A sampling rate outside (0, 1000] Hz; a History Event that has not Ended, or whose name matches no Rule; one Channel name used both as a Pulse Channel and as an ordinary one.
- While running: a timestamp not after the previous Sample or advance (or, with History, not after its latest end); a NaN, infinite or null value (the Sample is then ignored); pushing after `close()`; the wrong `push` for the sampling mode.
- A Rule referring to itself, to a Rule added after it, or to one not added at all (`build()`).
- Reading the Aggregates of a Channel the Rule does not aggregate.

The [Restarts: History](../history/) and [Silent sensors](../silent-sensors/) pages show restarts and time advances that stay within these rules. What the Retention Limit is, and what happens when a window reaches it, is on [Memory and the Retention Limit](../memory/).

## Reference

- [Errors and validation](https://github.com/romanhaendler/rbed#errors-and-validation) in the README, the canonical text, with the method that reports each mistake

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`ChannelExpression.averageOverSamples`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverSamples%28int%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Conditions.during`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#during%28io.github.romanhaendler.rbed.Rule%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.retentionLimit`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#retentionLimit%28int%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
