# Silent sensors: advancing time

A Detector evaluates its Rules only when something happens. If Samples stop arriving, nothing happens: a duration is never reached, and a sensor that has fallen silent goes unnoticed. `advanceTo(timestamp)` tells the Detector that time has passed without new data, typically called by a timer.

## Time without Samples

`x.age()` is the number of milliseconds since `x` last had a value, measured at the current Sample or advance; before the first value it is undefined. At a Sample that carries `x` it is 0. It grows at Samples that do not carry `x` and at advances, so when no Samples arrive at all, only advances make it grow:

```java
Rule sensorLost = Rule.named("SensorLost")
    .when(hr.age().above(Duration.ofSeconds(10).toMillis()));
Detector detector = Detector.builder()
    .rules(sensorLost)
    .build();

detector.push(0, Map.of(hr, 72.0));
detector.advanceTo(5_000);    // hr is 5 s old
detector.advanceTo(12_000);   // hr is 12 s old: SensorLost Confirmed
detector.push(13_000, Map.of(hr, 70.0));   // SensorLost Ended at 13000
```

## A heart rate that falls silent

A heart rate is reported every second. From 12 s it is high, and from 25 to 44 s the sensor is silent. `HighHeartRate` trusts a high reading only while it is recent: `hr` above 120 and not older than the limit. `SensorLost` is the opposite, a reading older than the limit. Without a Sample, a timer advances time once a second, and rbed computes `hr.age()` at every Sample and every advance.

<!-- live -->
The small marks just above the lanes are the moments a timer advanced time, once a second while no Sample arrived. The dashed line under the Channel is `hr.age()` in seconds, as rbed computes it at every Sample and every advance, with the limit as a line.

<!-- scenario silent-sensors draw -->

At the defaults, with the timer, `hr.age()` grows by a second at every advance after the last reading at 24 s. At 30 s it is 6 s old, more than the limit of 5 s: `HighHeartRate` Ends there and `SensorLost` is Confirmed, both without a Sample. `SensorLost` Ends at 45 s, when the sensor reports again.

### Things to try

- Raise *Silent after* to 10 s: both move later, to 35 s.
- Choose "Nothing happens" instead of the timer: the Detector learns of the silence only when the next Sample arrives at 45 s. That Sample carries `hr`, so its age is 0 again, and `hr.age()`, read only at Samples, is 0 at every one of them: there is no `SensorLost` Event, and `HighHeartRate` runs through the whole silence on a reading that is 21 seconds old by the time the next one arrives.

## What an advance does

At an advance, every Rule is evaluated with each Channel holding its last value (a [Pulse Channel](../pulse-channels/) is absent): durations can be reached, `totalDuration` and `count(...).within(...)` move on, and a `maxGap` is noticed. An advance is not a Sample, though: it does not count for [`forSamples`](../event-lifecycle/#the-start-lies-before-the-confirmation), nor for the Sample-based Aggregates `min`, `max`, `mean` and `count`. The time-weighted Aggregates do include the time up to it.

An advance must lie after the previous Sample or advance, and a Detector with a sampling rate rejects it, because there time advances with the Samples.

## Reference

- [Silent sensors: advancing time](https://github.com/romanhaendler/rbed#silent-sensors-advancing-time) in the README, the canonical text
- [Detector](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html) in the Javadoc: `advanceTo`
- The [Healthcare](../healthcare/) example: a sensor dropout during which the monitor advances its Detector every second

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`ChannelExpression.age`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#age%28%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Condition.not`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#not%28%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.advanceTo`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#advanceTo%28long%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
