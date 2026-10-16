# Automotive

A vehicle reports its speed (m/s) and engine rpm at 1 Hz. Here the engine runs while the vehicle stands, then the vehicle accelerates at 2 m/s² to 20 m/s, drives, brakes at 5 m/s² to a stop and stands again with the engine running.

<!-- scenario automotive -->

## The Rules

- **Trip**: moving faster than 1 m/s for 10 s, over after standing still for 3 minutes. Its distance, the integral of speed over time, is computed by a plain method that the listener calls for each Ended Trip, and shown as a note in the table: each speed from 301 to 903 s holds for 1 s, 11.94 km in all.
- **HarshBraking**: decelerating faster than 4 m/s², a `rate` below -4.
- **Idling**: standing with the engine running for 2 minutes. The last one still runs when the data ends at 1499 s, so its end is Truncated.

### Things to try

- Raise *Trip over after standing* to 10 minutes: the vehicle stands for less than that before the data ends, so the `Trip` runs to the end with a Truncated end.

## Reference

The example is [FleetTelematicsExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/FleetTelematicsExample.java), a test with its expected Events, listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples).

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Rule.endWhen`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`Expression.below`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`ChannelExpression.rate`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#rate%28%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.listener`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`EventListener.onEnded`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onEnded%28io.github.romanhaendler.rbed.Event%29), [`Event.is`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#is%28io.github.romanhaendler.rbed.Rule%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29), [`Event.aggregates`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#aggregates%28io.github.romanhaendler.rbed.Channel%29), [`Aggregates.integral`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Aggregates.html#integral%28%29).

The concepts at work, each on a page of its own: [Conditions and Expressions](../conditions-and-expressions/), [Aggregates](../aggregates/), [End Conditions and hysteresis](../end-conditions/).
