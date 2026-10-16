# Conditions and Expressions

What a Rule actually compares: Channel values, rates, moving averages and arithmetic between Channels.

A Rule takes Conditions, and Conditions are built from two kinds of building blocks:

- An **Expression** is a number that changes over time: a Channel's value, a rate, a moving average, a count of Events. `temp` is an Expression.
- A **Condition** is true, false or undefined at each Sample: `temp.above(80)` is a Condition.

Expressions are compared to get Conditions, and Conditions are combined with `and`, `or` and `not`. Every Expression supports every comparison and every arithmetic operation.

## What the Condition sees

One Rule, `AboveLimit`, compares an Expression of a noisy temperature with a fixed threshold. Pick the Expression: the dashed line is its value at every Sample, and the horizontal line is the threshold. The values are computed by rbed itself, not by the page. Where the Expression is undefined, the dashed line is absent and the stretch is shaded.

The temperature starts warm, has a single outlier at 15 s and ramps up from 30 s. The Channel `ambient` reports only from 5 s on.

<!-- scenario expressions draw -->

Things to try:

- **Moving average over Samples**: the temperature is above 24 from the first Sample, but the average of 5 Samples is undefined until the fifth. The first Event starts at 4 s, Truncated at its start. Raise the Samples averaged to 10 and the warm start is averaged away.
- **Rate**: the outlier at 15 s is a single step of 8.2 °C in one second, so the rate crosses its threshold and makes an Event of its own. A rate shows noise in full.
- **Moving average over a duration**: undefined until the values span the window. With 10 s, the warm start lies entirely in the warm-up and leaves no Event.
- **Arithmetic between Channels**: `temp.minus(ambient)` is undefined until `ambient` has a value. Arithmetic does not smooth: the outlier makes an Event here, too.

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

A threshold tells you *where* a signal is; a trend tells you *where it is going*, so a Rule can react before a threshold is reached.

- `rising()` and `falling()` are Conditions: the Channel's latest value is greater, or less, than its previous one. An unchanged value is neither. Combine them with `forSamples` for a sustained trend, or with `and` and a level, so that a high value that is still climbing counts and one that is already falling does not.
- `rate()` is an Expression: the change per second between the two latest values, `(latest - previous) / seconds between them`, so irregular sampling is handled correctly.

```java
// hard braking: speed in m/s drops by more than 4 m/s per second
Rule hardBraking = Rule.named("HardBraking").when(speed.rate().below(-4));
```

All three are undefined until the Channel has two values. They use the Channel's own values, not Samples: if a Channel is missing from a Sample, they still compare its two latest reported values. Because `rate()` looks at only two values, take the rate of a moving average for noisy signals: `level.averageOverSamples(4).rate()`.

## Moving averages

A moving average replaces each value with the mean of the most recent ones, so a Rule sees the underlying trend instead of the noise.

```java
level.averageOverSamples(5)                          // mean of the last 5 values
level.averageOverDuration(Duration.ofMinutes(10))    // mean of the last 10 minutes
```

`averageOverSamples(n)` is defined once `n` values exist; use it for regularly sampled data. `averageOverDuration` is defined once the values span the window; use it when Samples arrive irregularly or the window has a meaning in time. A longer window smooths more but reacts later. A moving average supports everything a Channel does, including `rate` and another average. Two averages of one Channel make a crossover signal:

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

A Condition is undefined when the data it needs does not exist yet: a Channel without a value so far, a moving average still warming up, `rate` before two values, a division by zero, or `latest(...)` before the first such Event. Undefined **never counts as true**, and that includes `not()`: `pressure.above(5).not()` is false, not true, as long as `pressure` has no value. So a Rule never fires because data is missing.

The README's chapter [Conditions and Expressions](https://github.com/romanhaendler/rbed#conditions-and-expressions) is the complete reference. The contracts are in the Javadoc of [Expression](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html), [ChannelExpression](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html) and [Condition](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html).
