# The latest Event

`latest(a)` gives access to the most recent Confirmed Event of Rule `a`, whether still running or Ended:

- `.min(x)`, `.max(x)`, `.mean(x)`: its Aggregates of Channel `x`, so far if it is running. Rule `a` aggregates `x` for this automatically.
- `.duration()`: its duration in milliseconds, up to now if it is running.

Each is an Expression, undefined while there has been no Event of `a`. This relates the present to an earlier Event.

## A partial set-down

A crane lifts a load of 800 kg from 10 s to 40 s and sets it down to 300 kg in between, from 25 s to 30 s. Then it lifts 600 kg from 60 s to 90 s and sets it down to 350 kg from 75 s to 80 s. `LoadLifted` has an End Condition, so a set-down does not End it. `PartialSetDown` holds while the weight is below a fraction of the latest lift's maximum. Hover over the chart: the readout names the `LoadLifted` Event that `latest` reads at that moment, and the chart highlights it.

<!-- scenario latest -->

At the default fraction of 0.5:

- Before 10 s there has been no lift, and `latest(loadLifted).max(weight)` is undefined.
- During the first lift `latest` reads that lift, whose maximum is 800 kg. The set-down to 300 kg is below 400 kg, and `PartialSetDown` runs from 25 s to 30 s.
- Between the lifts `latest` still reads the first one, which has Ended, but `during(loadLifted)` does not hold.
- From 60 s `latest` reads the second lift. Its maximum is 600 kg, so its set-down to 350 kg is not below 300 kg, although it would be below half of the first lift's 800 kg.

Raise the fraction to 0.6 and the second set-down counts too: 350 kg is below 360 kg, and `PartialSetDown` runs from 75 s to 80 s.

## A cycle taking too long

`.duration()` of a running Event grows with time, so a Rule can detect a long Event while it is still running:

```java
Rule cycle = Rule.named("Cycle").when(spindle.above(0.5));
Rule slowCycle = Rule.named("SlowCycle")
    .when(during(cycle).and(latest(cycle).duration().above(120_000)));
```

The [Cranes](../cranes/) example uses `PartialSetDown` with lifting, overload and classifying a load.

## Reference

- [latest: values of the most recent Event](https://github.com/romanhaendler/rbed#latest-values-of-the-most-recent-event) and [Aggregates](https://github.com/romanhaendler/rbed#aggregates) in the README, the canonical text
- [LatestEvent](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/LatestEvent.html) in the Javadoc
