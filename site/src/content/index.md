# rbed

Rule-based event detection in time series, for Java.

A small Java 17 library that finds **Events** in multi-channel time series by evaluating declarative **Rules**, on a live stream or over a recorded series. Zero runtime dependencies.

You describe *what* an Event is ("the weight is above 500 kg for 2 seconds"), and rbed tells you *when* it happened: when it started, when it was confirmed, when it ended, and what the data looked like in between. Every chart on this site is the real rbed, compiled to JavaScript and running in your browser.

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

## A first Rule

`HighTemperature` is an Event while the Channel `temp` is above a threshold. Move the threshold and the Detector runs again on the same three Samples. Or set Draw to `temp` and draw the values yourself, with the mouse or a finger.

<!-- scenario first-rule draw -->

Three steps are always the same:

1. **Build a Detector** from the Rules and a listener.
2. **Feed it Samples** with `push(timestamp, values)`, in time order.
3. **Close it** with `close()` when the data ends, so that an Event still in progress is reported, too.

## Domain examples

Runnable end-to-end examples from different domains, each a test with its expected Events; those with a page of their own run live here.

- [**Cranes** `CraneScenarioTest` hysteresis, during, classifying a load, partial set-down via latest](cranes/)
- [**Manufacturing** `MachineToolOverheatExample` sampling-rate mode, rate, combined durings](manufacturing/)
- [**Building services** `HvacShortCyclingExample` after().within(), count().within()](building-services/)
- [**Healthcare** `VitalSignsExample` advanceTo and age() for a silent sensor, maxGap](healthcare/)
- [**Automotive** `FleetTelematicsExample` deceleration via rate, distance via integral](automotive/)
- [**Finance** `TradingSignalsExample` moving-average crosses, truncated start after warm-up](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/TradingSignalsExample.java)
- [**Logistics** `ColdChainExample` attributing a cause with after, totalDuration limit](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/ColdChainExample.java)
- [**Environment** `FloodWarningExample` smoothed rate of a moving average, hysteresis](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/FloodWarningExample.java)
- [**Sports** `IntervalTrainingExample` after, rep count via count, listener-side timing](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/IntervalTrainingExample.java)
- [**IT operations** `DeploymentRegressionExample` a Pulse Channel reporting deployments](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/DeploymentRegressionExample.java)

[Source on GitHub](https://github.com/romanhaendler/rbed) · [Maven Central](https://central.sonatype.com/artifact/io.github.romanhaendler/rbed) · [MIT licence](https://github.com/romanhaendler/rbed/blob/main/LICENSE)
