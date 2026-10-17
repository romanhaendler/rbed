# Conditions and Expressions

What a Rule actually compares: Channel values, rates, moving averages and arithmetic between Channels.

A Rule takes Conditions, and Conditions are built from two kinds of building blocks:

- An **Expression** is a number that changes over time: a Channel's value, a rate, a moving average, a count of Events. `temp` is an Expression.
- A **Condition** is true, false or undefined at each moment (a Sample or a [time advance](../silent-sensors/)): `temp.above(80)` is a Condition.

Expressions are compared to get Conditions, and Conditions are combined with `and`, `or` and `not`. Every Expression supports every comparison and every arithmetic operation.

## What the Condition sees

One Rule, `AboveLimit`, compares an Expression of a noisy temperature with a fixed threshold. Choose the Expression to see how each one turns the same data into a different Condition.

<!-- live -->
The dashed line is the Expression's value at every Sample, and the horizontal line is the threshold. The values are computed by rbed itself, not by the page. Where the Expression is undefined, the dashed line is absent and the stretch is shaded.

The temperature starts warm, has a single outlier at 15 s and ramps up from 30 s. The Channel `ambient` reports only from 5 s on.

<!-- scenario expressions draw -->

At the default, a moving average over 5 Samples, `AboveLimit` runs from 4 s, where the average is first defined, to 7 s, and again from 37 s, once the ramp has lifted the average above 24, to the end of the data.

### Things to try

- **Moving average over Samples**: the temperature is above 24 from the first Sample, but the average of 5 Samples is undefined until the fifth. The first Event starts at 4 s, Truncated at its start. Raise the Samples averaged to 10 and the warm start is averaged away.
- **Rate**: the outlier at 15 s is a single step of 8.2 °C in one second, so the rate crosses its threshold and makes an Event of its own; the ramp from 30 s makes a second one, from 31 s to 39 s. A rate shows noise in full.
- **Moving average over a duration**: undefined until the values span the window. With the default 10 s, the warm start lies entirely in the warm-up and leaves no Event; the ramp makes one from 40 s.
- **Arithmetic between Channels**: `temp.minus(ambient)` is undefined until `ambient` has a value. Arithmetic does not smooth: the outlier makes an Event here, too, and the ramp one from 38 s.

## Comparisons

```java
temp.above(80)              // temp > 80, strictly
temp.below(-5)              // temp < -5, strictly
temp.between(18, 24)        // 18 <= temp <= 24, both bounds included
```

`above` and `below` also compare two Expressions, so a threshold can itself be a Channel, such as `weight.above(ratedLoad)`. To test "at least" or "at most", negate the opposite: `temp.below(80).not()` is `temp >= 80`.

## Arithmetic

Expressions support `plus`, `minus`, `times` and `dividedBy`, each with another Expression or a number, and `abs()`:

```java
supplyTemp.minus(returnTemp).above(5)     // temperature spread
current.times(voltage).above(2_000)       // power from two Channels
setpoint.minus(actual).abs().above(2)     // deviation in either direction
errors.dividedBy(requests).above(0.01)    // error rate above 1 %
```

A division by zero, or any other operation without a finite result, is undefined.

## Trends: rising, falling and rate

A threshold tells you *where* a Channel is; a trend tells you *where it is going*, so a Rule can react before a threshold is reached.

- `rising()` and `falling()` are Conditions: the Channel's latest value is greater, or less, than its previous one. An unchanged value is neither. Combine them with [`forSamples`](../event-lifecycle/#durations-forduration-and-forsamples) for a sustained trend, or with `and` and a level, so that a high value that is still climbing counts and one that is already falling does not.
- `rate()` is an Expression: the change per second between the two latest values, `(latest - previous) / seconds between them`, so irregular sampling is handled correctly.

```java
// hard braking: speed in m/s drops by more than 4 m/s per second
Rule hardBraking = Rule.named("HardBraking").when(speed.rate().below(-4));
```

All three are undefined until the Channel has two values. They use the Channel's own values, not Samples: if a Channel is missing from a Sample, they still compare its two latest reported values. Because `rate()` looks at only two values, take the rate of a moving average for noisy Channels: `level.averageOverSamples(4).rate()`.

## Moving averages

A moving average replaces each value with the mean of the most recent ones, so a Rule sees the underlying trend instead of the noise.

```java
level.averageOverSamples(5)                          // mean of the last 5 values
level.averageOverDuration(Duration.ofMinutes(10))    // mean of the last 10 minutes
```

`averageOverSamples(n)` is defined once `n` values exist; use it for regularly sampled data. `averageOverDuration` is defined once the values span the window; use it when Samples arrive irregularly or the window has a meaning in time. A longer window smooths more but reacts later. A moving average supports everything a Channel does, including `rate` and another average. Two averages of one Channel make a crossover:

```java
Rule goldenCross = Rule.named("GoldenCross").when(
    price.averageOverSamples(50).above(price.averageOverSamples(200)));
```

While an average warms up it is undefined, so an Event that starts right after the warm-up is Truncated at its start: the Rule could not tell whether the Condition held earlier.

## Logic: and, or, not

- `and`: all criteria must hold, which makes a Rule specific.
- `or`: any criterion suffices, which merges several ways into the same situation.
- `not`: the opposite, for "outside a band" or "at least".

```java
temp.above(80).and(fan.below(0.5))                          // cooling failure
temp.between(18, 24).not()                                   // outside the comfort band
rpm.above(100).and(vibration.above(4).or(temp.above(80)))   // calls nest explicitly
```

## Undefined values

A Condition is undefined when the data it needs does not exist yet: a Channel without a value so far, a moving average still warming up, `rate` before two values, a division by zero, or `latest(...)` before the first such Event. Undefined **never counts as true**, and that includes `not()`: `pressure.above(5).not()` is false, not true, as long as `pressure` has no value. So a Rule never produces an Event because data is missing.

## Reference

- [Conditions and Expressions](https://github.com/romanhaendler/rbed#conditions-and-expressions) in the README, the canonical text
- [Expression](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html), [ChannelExpression](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html) and [Condition](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html) in the Javadoc

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`ChannelExpression.rate`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#rate%28%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`ChannelExpression.averageOverSamples`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverSamples%28int%29), [`ChannelExpression.averageOverDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverDuration%28java.time.Duration%29), [`Expression.minus`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#minus%28io.github.romanhaendler.rbed.Expression%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29), [`Expression.below`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`Expression.between`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#between%28double,double%29), [`Expression.times`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#times%28io.github.romanhaendler.rbed.Expression%29), [`Expression.abs`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#abs%28%29), [`Expression.dividedBy`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#dividedBy%28io.github.romanhaendler.rbed.Expression%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Condition.not`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#not%28%29), [`Condition.or`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#or%28io.github.romanhaendler.rbed.Condition%29), [`ChannelExpression.rising`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#rising%28%29), [`ChannelExpression.falling`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#falling%28%29), [`Expression.plus`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#plus%28double%29).
