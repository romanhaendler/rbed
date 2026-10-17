# rbed

Rule-based event detection in time series, for Java.

A small Java 17 library that finds **Events** in multi-channel time series by evaluating declarative **Rules**, on a live stream or over a recorded series. Zero runtime dependencies.

You describe *what* an Event is ("the weight is above 500 kg for 2 seconds"), and rbed tells you *when* it happened: when it started, when it was confirmed, when it ended, and what the data looked like in between. Every chart on this site is the real rbed, compiled to JavaScript and running in your browser.

The terms (Sample, Channel, Condition, Rule, Event, Detector, Pending, Confirmed, Truncated, ...) are defined in the [glossary](https://github.com/romanhaendler/rbed/blob/main/CONTEXT.md).

## A first Rule

`HighTemperature` is an Event while the Channel `temp` is above a threshold.

<!-- live -->
Move the threshold and the Detector runs again on the same three Samples. Or set Draw to `temp` and draw the values yourself, with the mouse or a finger.

<!-- scenario first-rule draw -->

At the default threshold of 80 °C, `HighTemperature` runs from 2 s to 3 s, and the listener prints "HighTemperature from 2000 to 3000": timestamps are milliseconds, and the site's tables show seconds.

### Things to try

- Lower the threshold to 70 °C: the Condition already holds at the first Sample (75 °C), so the Event starts at 1 s with a Truncated start.
- Raise it to 90 °C: no Sample is above it, and there is no Event.

### Three steps

Every use of rbed takes the same three steps:

1. **Build a Detector** from the Rules and a listener.
2. **Feed it Samples** with `push(timestamp, values)`, in time order.
3. **Close it** with `close()` when the data ends, so that an Event still in progress is reported, too.

For a recorded series, [`detect`](sampling-rate/) does all three in one call, and a Detector is `AutoCloseable`, so try-with-resources works.

## Install

rbed {{version}} is on Maven Central and needs Java 17 or later.

```xml Maven
<dependency>
  <groupId>io.github.romanhaendler</groupId>
  <artifactId>rbed</artifactId>
  <version>{{version}}</version>
</dependency>
```

```kotlin Gradle
implementation("io.github.romanhaendler:rbed:{{version}}")
```

## The code on this site

The Java on every page can be copied and run: it starts with its imports, and its statements are the body of a method, such as `main`. Where it calls a method of its own, that method follows under `// In the enclosing class:`. Time values are written as `java.time.Duration`; every method that takes one also takes a `long` in milliseconds. Short snippets in the text assume the Channels, Rules, variables and methods they name, and these imports:

```java
import static io.github.romanhaendler.rbed.Conditions.*;   // during, after, count, totalDuration, latest

import io.github.romanhaendler.rbed.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
```

`EventListener` shares its simple name with `java.util.EventListener`: next to `import java.util.*;`, import `io.github.romanhaendler.rbed.EventListener` explicitly.

## Good to know

- A Detector is single-use and not thread-safe. Use one per data stream, and feed it from one thread at a time.
- Rules and the listener are fixed when the Detector is built. To change Rules, build a new Detector and pass it the Events so far as [History](history/).
- A pushed Sample costs one hash lookup per value, and in steady state (no Event starting or ending) a Detector allocates nothing: from about 1.2 million Samples per second for 50 Rules over 20 Channels to over 40 million for a single threshold Rule. See the README's [Performance](https://github.com/romanhaendler/rbed#performance), and [Memory and the Retention Limit](memory/) for what a Detector keeps.

Methods used: [`Channel.named`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Detector.builder`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.listener`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`EventListener.onEnded`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onEnded%28io.github.romanhaendler.rbed.Event%29), [`Event.name`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#name%28%29), [`Event.start`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#start%28%29), [`Event.end`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#end%28%29), [`Detector.Builder.build`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).

The Guide takes one concept per page, starting with [Samples and Channels](samples-and-channels/).

## Domain examples

Runnable end-to-end examples from different domains, each a test with its expected Events and a page of its own where it runs live.

- [**Cranes** `CraneScenarioTest` hysteresis, during, classifying a load, partial set-down via latest](cranes/)
- [**Manufacturing** `MachineToolOverheatExample` sampling-rate mode, rate, combined durings](manufacturing/)
- [**Building services** `HvacShortCyclingExample` after().within(), count().within()](building-services/)
- [**Healthcare** `VitalSignsExample` advanceTo and age() for a silent sensor, maxGap](healthcare/)
- [**Automotive** `FleetTelematicsExample` deceleration via rate, distance via integral](automotive/)
- [**Finance** `TradingSignalsExample` moving-average crosses, Truncated start after warm-up](finance/)
- [**Logistics** `ColdChainExample` attributing a cause with after, totalDuration limit](logistics/)
- [**Environment** `FloodWarningExample` smoothed rate of a moving average, hysteresis](environment/)
- [**Sports** `IntervalTrainingExample` after, rep count via count, listener-side timing](sports/)
- [**IT operations** `DeploymentRegressionExample` a Pulse Channel reporting deployments](it-operations/)

[Source on GitHub](https://github.com/romanhaendler/rbed) · [Maven Central](https://central.sonatype.com/artifact/io.github.romanhaendler/rbed) · [MIT licence](https://github.com/romanhaendler/rbed/blob/main/LICENSE)
