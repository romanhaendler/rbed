# Pulse Channels

Some Channels report one-off occurrences rather than a state: a deployment, a button press, a trade. An ordinary Channel keeps its last value until the next one arrives, so a `deploy` value of 1 would stay 1 forever. A Pulse Channel's value exists only in the Sample that carries it.

## Declaring one

Create the Channel with `Channel.pulse(name)` instead of `Channel.named(name)`. Everything else stays the same: push its value in the Samples that carry an occurrence, and leave it out of the others.

## Deployments and errors

A service reports its errors once a minute, and the Pulse Channel `deploy` reports its deployments, at minutes 10 and 60. `Deployment` is an Event while a deployment is reported, which is exactly one Sample: at the next Sample `deploy` is absent, and the Event Ends. `Regression` is an error spike within a window after a Deployment, written with `after(deployment).within(...)`.

The `deploy` values are drawn as marks at their Samples, not as a line, because there is no value between them. Widen the window to 45 minutes, and the second spike, 40 minutes after the second Deployment, is a Regression too.

<!-- scenario pulse-channels draw -->

## Absent is not undefined

In every Sample that does not carry it, and at every time advance, a Pulse Channel is *absent*. Absent is not undefined, because the absence of a pulse is known:

- Comparisons, `rising` and `falling` on an absent pulse are **false**, so `not()` of them is **true**: `deploy.above(0.5).not()` means "no deployment now".
- Arithmetic on an absent pulse is undefined.
- `age()` is the time since the last pulse, for example `heartbeat.age().above(60_000)` for a heartbeat that has gone quiet.

The README's [Pulse Channels](https://github.com/romanhaendler/rbed#pulse-channels) chapter is the reference for this page.

Methods used: [`pulse`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#pulse%28java.lang.String%29), [`when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`after`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28long%29), [`and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29).
