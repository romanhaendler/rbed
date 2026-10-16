# Automotive

A vehicle reports its speed (m/s) and engine rpm at 1 Hz. Here the engine runs while the vehicle stands, then the vehicle accelerates at 2 m/s² to 20 m/s, drives, brakes at 5 m/s² to a stop and stands again with the engine running.

<!-- scenario automotive -->

## The Rules

- **Trip**: moving faster than 1 m/s for 10 s, over after standing still for 3 minutes. Its distance is the integral of speed over time, shown as a note in the table: each speed from 301 to 903 s holds for 1 s, 11940 m in all.
- **HarshBraking**: decelerating faster than 4 m/s², a `rate` below -4.
- **Idling**: standing with the engine running for 2 minutes. The last one still runs when the data ends at 1499 s, so its end is Truncated.

## Source and reference

The example is [FleetTelematicsExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/FleetTelematicsExample.java), listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples) with its expected Events.

Methods used: [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`endWhen`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`below`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`rate`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#rate%28%29), [`and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Event.aggregates`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#aggregates%28io.github.romanhaendler.rbed.Channel%29), [`Aggregates.integral`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Aggregates.html#integral%28%29).

The concepts at work, each on a page of its own: [Conditions and Expressions](../conditions-and-expressions/), [Aggregates](../aggregates/), [End Conditions and hysteresis](../end-conditions/).
