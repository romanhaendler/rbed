# Samples and Channels

A **Sample** is the values of one or more **Channels** at one timestamp. Timestamps are milliseconds (`long`), for example epoch milliseconds or milliseconds since the start of a recording, and they must strictly increase: a Sample at or before the previous one is rejected.

## Channels

Each Channel is created once, by name, and then used everywhere: in Conditions, when pushing values and when reading Aggregates. The name is written in one place, and the compiler checks every use. In an application, Channels are typically `static final` constants.

```java
Channel temp = Channel.named("temp");
Channel humidity = Channel.named("humidity");
```

Channels are equal by name, so data that arrives with names as strings, such as a CSV row or a JSON object, maps onto the same Channels without a registry:

```java
Map<Channel, Double> values = new HashMap<>();
row.forEach((column, value) -> values.put(Channel.named(column), value));
detector.push(timestamp, values);
```

Values of Channels that no Rule uses are ignored, so a Sample may carry more Channels than the Rules need. Values must be finite: a NaN or infinite value is rejected.

## Sensors at different rates

A Sample does not have to contain every Channel. A Channel missing from a Sample keeps its last value, so sensors reporting at different rates can share one Detector. Here a room's temperature `temp` reports every second and rises from 21 °C to 30 °C. Its humidity reports less often; it rises from 50 % to 65 % at 30 s and falls again. `Muggy` is an Event while the temperature is above 26 °C and the humidity above 60 %. Between two reports, `humidity` is the value of the last one.

<!-- live -->
Hover over the chart to read both Channels at each Sample.

<!-- scenario samples-and-channels -->

At the default of a humidity report every 10 seconds:

- `temp` is above 26 °C from 11 s, but `humidity` is not above 60 % in any report until the one at 30 s (65 %). `Muggy` starts there, although the humidity actually rose above 60 % at 21 s: rbed knows only the values it is given.
- From 31 s to 39 s the Samples carry only `temp`, and `humidity` keeps its 65 %. The report at 40 s reads exactly 60 %, which is not above 60, and `Muggy` Ends.

### Things to try

- Report the humidity every second: `Muggy` runs from 21 s to 40 s, where the humidity really is above 60 %.
- Report it every 7 seconds: the reports from 21 s to 35 s are above 60 %, and the next one comes at 42 s. `Muggy` runs from 21 s until 42 s, two seconds longer than the humidity stayed above 60 %, because the held value says so.

A Channel that has had no value yet is *undefined*, and a Condition on it never holds (see [Conditions and Expressions](../conditions-and-expressions/#undefined-values)). To bound how long a held value may be trusted, use [`maxGap`](../data-gaps/) for the gap between Samples or [`age()`](../silent-sensors/) for a single Channel.

## Reference

- [Samples and Channels](https://github.com/romanhaendler/rbed#samples-and-channels) in the README, the canonical text
- [Channel](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html) and [Sample](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Sample.html) in the Javadoc

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
