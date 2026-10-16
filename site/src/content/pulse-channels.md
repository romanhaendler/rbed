# Pulse Channels

Some Channels report one-off occurrences rather than a state: a deployment, a button press, a trade. An ordinary Channel keeps its last value until the next one arrives, so a `deploy` value of 1 would stay 1 forever. A Pulse Channel's value exists only in the Sample that carries it.

## Declaring one

Create the Channel with `Channel.pulse(name)` instead of `Channel.named(name)`. Everything else stays the same: push its value in the Samples that carry an occurrence, and leave it out of the others.

## Deployments and errors

A service reports its errors once a minute, and the Pulse Channel `deploy` reports its deployments, at minutes 10 and 60. `Deployment` is an Event while a deployment is reported, which is exactly one Sample: at the next Sample `deploy` is absent, and the Event Ends. `Regression` is an error spike within a window after a Deployment, written with `after(deployment).within(...)`.

<!-- live -->
The `deploy` values are drawn as marks at their Samples, not as a line, because there is no value between them.

<!-- scenario pulse-channels draw -->

At the defaults, the spike at minute 25 starts 14 minutes after the first Deployment Ended (minute 11) and is a `Regression`. The second spike, at minute 100, starts 39 minutes after the second Deployment Ended (minute 61), outside the 30-minute window.

### Things to try

- Widen the window to 45 minutes: the second spike is a Regression too.
- Set it to 40 minutes: the second spike counts only until minute 101, the last minute within 40 minutes of the Deployment's end, so its `Regression` runs from 100 to 102.

## Absent is not undefined

In every Sample that does not carry it, and at every time advance, a Pulse Channel is *absent*. Absent is not undefined, because the absence of a pulse is known:

- Comparisons, `rising` and `falling` on an absent pulse are **false**, so `not()` of them is **true**: `deploy.above(0.5).not()` means "no deployment now".
- Arithmetic on an absent pulse is undefined.
- `age()` is the time since the last pulse: a `heartbeat.age()` above a minute is a heartbeat that has gone quiet.

## Reference

- [Pulse Channels](https://github.com/romanhaendler/rbed#pulse-channels) in the README, the canonical text
- [Channel](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html) in the Javadoc

Methods used: [`Channel.pulse`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#pulse%28java.lang.String%29), [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Conditions.after`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28java.time.Duration%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
