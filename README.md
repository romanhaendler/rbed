# rbed: rule-based event detection

A small Java 17 library that finds **Events** in multi-channel time series by evaluating declarative **Rules**, on a live stream or over a recorded series. Zero runtime dependencies.

Vocabulary (Sample, Channel, Condition, Rule, Event, Detector, ...) is defined in [CONTEXT.md](CONTEXT.md).

## Install

Not yet published. Build and install locally:

```sh
mvn install
```

```xml
<dependency>
  <groupId>io.github.rbed</groupId>
  <artifactId>rbed</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## A first Rule

```java
import static rbed.Conditions.*;

Detector detector = Detector.builder()
    .rule("HighTemperature", r -> r.when(channel("temp").above(80)))
    .listener(new EventListener() {
      @Override public void onEnded(Event e) {
        System.out.println(e.name() + " from " + e.start() + " to " + e.end().getAsLong());
      }
    })
    .build();

detector.push(1_000, Map.of("temp", 75.0));   // timestamp in ms, then Channel values
detector.push(2_000, Map.of("temp", 85.0));
detector.push(3_000, Map.of("temp", 70.0));   // prints "HighTemperature from 2000 to 3000"
detector.close();
```

## Event lifecycle

Each Rule has at most one Event in progress. The listener's methods are all optional:

| Callback      | When                                                                  |
| ------------- | --------------------------------------------------------------------- |
| `onPending`   | The Conditions started holding; the required duration is not reached. |
| `onConfirmed` | The Conditions held long enough. Only Confirmed Events count.         |
| `onEnded`     | A Confirmed Event is over. It has its end, Aggregates and attributes. |
| `onDiscarded` | A Pending Event stopped holding before it was Confirmed.              |

`close()` Ends a Confirmed Event with `truncatedEnd()` and Discards a Pending one. An Event that may have begun before observation, because its Rule had not yet seen its Conditions not holding (at the start, during a warm-up, or after a data gap), has `truncatedStart()`.

A new Event of a Rule only starts after its `when` Condition has stopped holding since the previous Event started. With an End Condition that overlaps `when`, a Rule therefore does not flicker.

## Building Rules

```java
.rule("LoadLifted", r -> r
    .when(channel("weight").above(500)).forDuration(Duration.ofSeconds(2))   // or .forSamples(5)
    .endWhen(channel("weight").below(50)).forDuration(Duration.ofSeconds(1)) // hysteresis
    .maxGap(Duration.ofSeconds(5))            // a longer data gap interrupts the Event
    .attribute("load", e -> e.aggregates("weight").mean() < 1500 ? "pallet" : "container"))
```

Conditions (`import static rbed.Conditions.*`):

- `channel("x").above(v)`, `.below(v)`, `.between(lo, hi)`. Each also accepts another expression, e.g. `channel("weight").above(channel("ratedLoad"))`.
- `channel("x").rising()`, `.falling()`, `.rate()` (per second), `.averageOverSamples(n)`, `.averageOverDuration(ms)`.
- `a.and(b)`, `a.or(b)`, `a.not()`.
- `during("A")`: an A Event is Confirmed and has not Ended yet.
- `after("A")`: an A Event has Ended. Narrow it with `.unless("C", ...)` (no C Confirmed since then) and `.within(ms)`.

A Channel with no value yet, or an average that is still warming up, makes a comparison *undefined*. Undefined counts as false, also under `not()`. A Sample may contain any subset of Channels: missing ones keep their last value. A Rule may only reference Rules declared before it.

## Batch

```java
List<Event> events = detector.detect(List.of(
    new Sample(0, Map.of("weight", 0.0)),
    new Sample(500, Map.of("weight", 1200.0)) /* ... */));
```

For regularly sampled data without timestamps, configure `.samplingRate(hz)` on the builder (at most 1000 Hz, since timestamps are milliseconds) and use `push(values)` / `detectValues(listOfMaps)`.

## Restarts

Pass Events stored before a restart with `.history(events)`, so that `after(...)` Rules keep working. They must have been Confirmed and have Ended before the first Sample.

## Examples

Runnable end-to-end examples from different domains, each a test with its expected Events:

| Domain           | Example                                                           | Shows                                                    |
| ---------------- | ----------------------------------------------------------------- | -------------------------------------------------------- |
| Cranes           | `src/test/java/rbed/CraneScenarioTest.java`                       | hysteresis, `during`, classifying a load via attributes  |
| Manufacturing    | `src/test/java/rbed/examples/MachineToolOverheatExample.java`     | sampling-rate mode, `rate`, combined `during`s           |
| Building services | `src/test/java/rbed/examples/HvacShortCyclingExample.java`       | `after().within()`, classifying by Event duration        |
| Healthcare       | `src/test/java/rbed/examples/VitalSignsExample.java`              | `maxGap` on sensor dropout, Truncated Events             |
| Automotive       | `src/test/java/rbed/examples/FleetTelematicsExample.java`         | deceleration via `rate`, trips, idling                   |
| Finance          | `src/test/java/rbed/examples/TradingSignalsExample.java`          | moving-average crosses, truncated start after warm-up    |
| Logistics        | `src/test/java/rbed/examples/ColdChainExample.java`               | attributing a cause with `after`, listener-side totals   |
| Environment      | `src/test/java/rbed/examples/FloodWarningExample.java`            | rising and falling levels, irregular reports             |
| Sports           | `src/test/java/rbed/examples/IntervalTrainingExample.java`        | reps and recovery times computed by a listener           |
| IT operations    | `src/test/java/rbed/examples/DeploymentRegressionExample.java`    | error spikes after a deployment marker                   |

## Performance

Conditions are bound to the Detector once, in `build()`. After that, pushing a Sample does not look anything up by name. In steady state (no Event starting or ending) it allocates nothing, so memory stays bounded however long an Event lasts. Moving averages use fixed ring buffers with compensated sums, so they neither allocate nor drift.

Measured with `mvn test -Pperformance` (Apple M2, JDK 21):

| Scenario                                                  | Throughput             |
| --------------------------------------------------------- | ---------------------- |
| One threshold Rule, Events every 100 Samples              | ~45 million Samples/s  |
| Realistic Rule set (durations, End Condition, during, after/unless, rate, moving averages) | ~11 million Samples/s |
| 50 Rules x 20 Channels, Events every 40 Samples           | ~0.37 million Samples/s |

Every running Event keeps Aggregates for all Channels, at about 1 ns per Event and Channel per Sample. With many Channels and many simultaneous Events, this dominates the cost.

The performance tests are excluded from the default build because timings depend on the machine. The allocation checks among them are deterministic.

## Notes

- A Detector is single-use and not thread-safe. Use one per data stream.
- Timestamps must strictly increase, and values must be finite. Otherwise `push` throws `IllegalArgumentException`.
- A complete worked example is the crane scenario test in `src/test/java/rbed/CraneScenarioTest.java`.
