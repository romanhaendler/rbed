# rbed: rule-based event detection

[![CI](https://github.com/romanhaendler/rbed/actions/workflows/ci.yml/badge.svg)](https://github.com/romanhaendler/rbed/actions/workflows/ci.yml) [![Maven Central](https://img.shields.io/maven-central/v/io.github.romanhaendler/rbed)](https://central.sonatype.com/artifact/io.github.romanhaendler/rbed)

**Try it in the browser:** [romanhaendler.github.io/rbed](https://romanhaendler.github.io/rbed/), interactive documentation that runs rbed live.

A small Java 17 library that finds **Events** in multi-channel time series by evaluating declarative **Rules**, on a live stream or over a recorded series. Zero runtime dependencies.

You describe *what* an Event is ("the weight is above 500 kg for 2 seconds", "the temperature rises again within 10 minutes after the door was opened"), and rbed tells you *when* it happened: when it started, when it was confirmed, when it ended, and what the data looked like in between.

Vocabulary (Sample, Channel, Condition, Rule, Event, Detector, ...) is defined in [CONTEXT.md](CONTEXT.md). This README walks through every capability of the library.

- [Install](#install)
- [A first Rule](#a-first-rule)
- [Samples and Channels](#samples-and-channels)
- [The Event lifecycle](#the-event-lifecycle)
- [Events](#events)
- [Building Rules](#building-rules): durations, End Conditions, data gaps, derived values
- [Conditions and Expressions](#conditions-and-expressions): comparisons, arithmetic, trends, moving averages, logic, reading values
- [Referring to other Rules](#referring-to-other-rules): `during`, `after`, `count`, `totalDuration`, `latest`
- [Aggregates](#aggregates)
- [Batch processing and sampling rates](#batch-processing-and-sampling-rates)
- [Silent sensors: advancing time](#silent-sensors-advancing-time)
- [Pulse Channels](#pulse-channels)
- [Restarts: History](#restarts-history)
- [Errors and validation](#errors-and-validation)
- [Examples](#examples), [Performance](#performance), [Notes](#notes), [License](#license)

## Install

rbed is on Maven Central and needs Java 17 or later. Maven:

```xml
<dependency>
  <groupId>io.github.romanhaendler</groupId>
  <artifactId>rbed</artifactId>
  <version>0.3.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("io.github.romanhaendler:rbed:0.3.0")
```

The library is the Java module `io.github.romanhaendler.rbed` (`requires io.github.romanhaendler.rbed;`) and has a single package of the same name. It also works on the class path.

## A first Rule

```java
import static io.github.romanhaendler.rbed.Conditions.*;

Channel temp = Channel.named("temp");

Rule highTemperature = Rule.named("HighTemperature").when(temp.above(80));
Detector detector = Detector.builder()
    .rules(highTemperature)
    .listener(new EventListener() {
      @Override public void onEnded(Event e) {
        System.out.println(e.name() + " from " + e.start() + " to " + e.end().getAsLong());
      }
    })
    .build();

detector.push(1_000, Map.of(temp, 75.0));   // timestamp in ms, then Channel values
detector.push(2_000, Map.of(temp, 85.0));
detector.push(3_000, Map.of(temp, 70.0));   // prints "HighTemperature from 2000 to 3000"
detector.close();
```

Three steps are always the same:

1. **Build a Detector**: define each Rule once with `Rule.named(name)`, then add the Rules to `Detector.builder()` with `.rules(...)` and a listener with `.listener(...)`, and call `.build()`. The Rule's name becomes the name of the Events it produces.
2. **Feed it Samples** with `push(timestamp, values)`, one at a time, in time order. Every Rule is evaluated at every Sample.
3. **Close it** with `close()` when the data ends, so that an Event still in progress is reported, too. A Detector is `AutoCloseable`, so try-with-resources works.

For a recorded series, `detect(samples)` does all three in one call and returns the Events as a list (see [Batch processing](#batch-processing-and-sampling-rates)).

## Samples and Channels

A **Sample** is the values of one or more **Channels** at one timestamp. Timestamps are milliseconds (`long`), for example epoch milliseconds or milliseconds since the start of a recording. They must strictly increase.

Each Channel is created once, by name, and then used everywhere: in Conditions, when pushing values and when reading Aggregates. The name is written in one place, and the compiler checks every use. The examples in this README assume their Channels are created like this (in an application they are typically `static final` constants):

```java
Channel temp = Channel.named("temp");
Channel pressure = Channel.named("pressure");
```

Channels are equal by name, so data that arrives with names as strings, such as a CSV row or a JSON object, maps onto the same Channels without a registry:

```java
Map<Channel, Double> values = new HashMap<>();
row.forEach((column, value) -> values.put(Channel.named(column), value));
detector.push(timestamp, values);
```

Values of Channels that no Rule uses are ignored, so a Sample may carry more Channels than the Rules need.

A Sample does not have to contain every Channel. A missing Channel keeps its last value, so sensors reporting at different rates can share one Detector:

```java
detector.push(0,     Map.of(temp, 20.0, pressure, 1.2));
detector.push(1_000, Map.of(temp, 21.0));           // pressure is still 1.2
detector.push(2_000, Map.of(pressure, 1.4));        // temp is still 21.0
```

A Channel that has not had any value yet is *undefined*, and a Condition on it never holds (see [Undefined values](#undefined-values)). Values must be finite: NaN and infinities are rejected.

## The Event lifecycle

An Event goes through up to four stages, and the listener is told about each. All four methods of `EventListener` are optional, so implement only the ones you need:

| Callback      | When                                                                  |
| ------------- | --------------------------------------------------------------------- |
| `onPending`   | The Conditions started holding; the required duration is not reached. |
| `onConfirmed` | The Conditions held long enough. Only Confirmed Events count.         |
| `onEnded`     | A Confirmed Event is over. It has its end and Aggregates.             |
| `onDiscarded` | A Pending Event stopped holding before it was Confirmed.              |

The distinction between Pending and Confirmed matters when a Rule requires a duration. Here a temperature must stay above 80 for two seconds:

```java
Rule overheat = Rule.named("Overheat").when(temp.above(80)).forDuration(2_000);
Detector detector = Detector.builder()
    .rules(overheat)
    .listener(new EventListener() {
      @Override public void onPending(Event e)   { System.out.println("pending at " + e.start()); }
      @Override public void onConfirmed(Event e) { System.out.println("confirmed at " + e.confirmed().getAsLong()); }
      @Override public void onEnded(Event e)     { System.out.println("ended at " + e.end().getAsLong()); }
      @Override public void onDiscarded(Event e) { System.out.println("discarded"); }
    })
    .build();

detector.push(0,     Map.of(temp, 70.0));
detector.push(1_000, Map.of(temp, 85.0));  // pending at 1000
detector.push(2_000, Map.of(temp, 86.0));
detector.push(3_000, Map.of(temp, 84.0));  // confirmed at 3000: held for 2 s
detector.push(4_000, Map.of(temp, 75.0));  // ended at 4000
detector.push(5_000, Map.of(temp, 90.0));  // pending at 5000
detector.push(6_000, Map.of(temp, 70.0));  // discarded: only held for 1 s
```

The Event's `start()` is 1000, where the Conditions started holding, not 3000, where it was Confirmed. Without a duration, an Event is Pending and Confirmed at the same moment.

Each Rule has at most one Event in progress. A new Event of a Rule only starts after its `when` Condition has stopped holding since the previous Event started. With an End Condition that overlaps `when` (see [End Conditions](#end-conditions-and-hysteresis)), a Rule therefore does not flicker.

**Truncation.** Two flags mark Events whose true extent is not known:

- `truncatedEnd()`: observation stopped while the Event was running. `close()` Ends a Confirmed Event this way (and Discards a Pending one), and so does a [data gap](#data-gaps-maxgap).
- `truncatedStart()`: the Event may have begun before its `start()`. This is the case whenever its Rule had not yet seen its Conditions *not* holding: when the very first Sample already satisfies them, while a moving average was warming up, or right after a data gap.

```java
detector.push(0,     Map.of(temp, 90.0));  // already hot at the first Sample
detector.push(1_000, Map.of(temp, 70.0));  // Overheat 0..1000 with truncatedStart() == true
```

## Events

An `Event` is an immutable record. The listener receives a fresh snapshot at each stage.

| Field              | Meaning                                                                        |
| ------------------ | ------------------------------------------------------------------------------ |
| `name()`           | The name of the Rule that produced it, e.g. to store the Event.                |
| `is(rule)`         | Whether `rule` produced it.                                                    |
| `start()`          | Timestamp (ms) of the first moment at which `when` held.                       |
| `confirmed()`      | `OptionalLong`: when it was Confirmed; empty while Pending or if Discarded.    |
| `end()`            | `OptionalLong`: when it Ended; empty until then.                               |
| `truncatedStart()` | It may have begun earlier than `start()`.                                      |
| `truncatedEnd()`   | Observation stopped while it ran: `close()` or a data gap.                     |
| `aggregates(x)`    | [Aggregates](#aggregates) of Channel `x` over the Event so far.                |

The duration of an Ended Event is `e.end().getAsLong() - e.start()`.

## Building Rules

A Rule is an immutable value: `Rule.named(name)` creates it, and every configuration method returns a new Rule, so a Rule can be shared by several Detectors. A complete Rule using every option:

```java
Rule loadLifted = Rule.named("LoadLifted")
    .when(weight.above(500)).forDuration(Duration.ofSeconds(2))
    .endWhen(weight.below(50)).forDuration(Duration.ofSeconds(1))
    .maxGap(Duration.ofSeconds(5))
    .aggregate(height);
```

Every time value accepts either a `long` in milliseconds or a `java.time.Duration`.

### `when`: the start Condition

`when(condition)` is the only required part. An Event starts when the Condition holds and, unless there is an End Condition, lasts as long as it holds. The Event Ends at the first moment the Condition no longer holds.

### Durations: `forDuration` and `forSamples`

A Condition that holds only briefly is often noise. `forDuration(ms)` requires the preceding Condition to hold for at least that long, `forSamples(n)` for at least `n` consecutive Samples:

```java
Rule vibration = Rule.named("Vibration").when(rms.above(4.5)).forSamples(3);
Rule stall = Rule.named("Stall").when(rpm.below(10)).forDuration(Duration.ofSeconds(5));
Rule both = Rule.named("Both").when(rpm.below(10)).forSamples(3).forDuration(5_000);
```

With both, both must be met: at least 3 Samples *and* at least 5 seconds. Until then the Event is Pending; if the Condition stops holding first, it is Discarded. `forDuration` measures from the Event's `start()`, so the required duration is reached at `start() + ms`.

`forDuration` and `forSamples` apply to whatever came right before them: `when(...)` or `endWhen(...)`.

### End Conditions and hysteresis

Real signals hover around thresholds. With `when(weight > 500)` alone, a weight fluctuating between 490 and 510 produces a new Event every few Samples. An End Condition fixes this: once Confirmed, the Event continues until the End Condition holds, whatever `when` does.

```java
Rule loadLifted = Rule.named("LoadLifted")
    .when(weight.above(500))
    .endWhen(weight.below(50));
```

```java
detector.push(0,     Map.of(weight, 0.0));
detector.push(1_000, Map.of(weight, 600.0));  // starts
detector.push(2_000, Map.of(weight, 300.0));  // below 500, but not below 50: continues
detector.push(3_000, Map.of(weight, 510.0));  // still the same Event, no new one
detector.push(4_000, Map.of(weight, 40.0));   // LoadLifted 1000..4000
```

The End Condition can have its own duration, written right after it: `.endWhen(weight.below(50)).forDuration(1_000)` Ends the Event only once the weight has stayed below 50 for a second. The Event's `end()` is then where the End Condition *started* holding, not where its duration was reached, so the Event's extent is what you would draw by hand.

While the End Condition's duration is running, the Event is still Confirmed and `during(...)` of it still holds.

### Data gaps: `maxGap`

If a sensor stops reporting, the last values would otherwise hold forever and an Event could run on stale data. `maxGap(ms)` bounds how far apart two Samples may be:

```java
Rule running = Rule.named("Running").when(rpm.above(100)).maxGap(Duration.ofSeconds(10));
```

When the next Sample (or [time advance](#silent-sensors-advancing-time)) comes more than 10 seconds after the previous Sample, a Pending Event is Discarded, and a Confirmed Event Ends with `truncatedEnd()` at the last moment evaluated before the gap. An Event that starts right after the gap has `truncatedStart()`, since its Rule has not seen its Conditions not holding since data resumed. Without `maxGap`, gaps of any length are accepted.

### Aggregates for more Channels: `aggregate`

Each Event carries [Aggregates](#aggregates) (min, max, mean, ...) of the Channels its Rule's Conditions use. To also get them for other Channels, name those Channels:

```java
Rule loadLifted = Rule.named("LoadLifted")
    .when(weight.above(500))
    .aggregate(height, windSpeed);   // weight is aggregated anyway
```

### Derived values

To classify an Ended Event or derive a figure from it, write a plain method of the Event: its Aggregates, start, end and flags are all there, and the result is typed.

```java
static boolean shortCycle(Event run) {
  return run.end().getAsLong() - run.start() < 5 * 60_000;
}

@Override public void onEnded(Event e) {
  if (shortCycle(e)) { /* ... */ }
}
```

### Rule order

Rules are evaluated in the order they are added with `rules(...)`, at every Sample. A Rule may refer to other Rules (see [Referring to other Rules](#referring-to-other-rules)), but only to Rules added *before* it. It therefore sees their state at the same Sample, and dependencies can never be circular. Since a Rule refers to another Rule object, not to a name, a misspelled reference does not compile, and `build()` rejects a reference to a Rule that was not added or is added later.

## Conditions and Expressions

Conditions are built from static factory methods:

```java
import static io.github.romanhaendler.rbed.Conditions.*;
```

There are two kinds of building blocks:

- An **Expression** is a number that changes over time: a Channel's value, a rate, a moving average, a count of Events. `temp` is an Expression.
- A **Condition** is true, false or [undefined](#undefined-values) at each moment: `temp.above(80)` is a Condition. Rules take Conditions.

Expressions are compared to get Conditions, and Conditions are combined with `and`, `or` and `not`. Every Expression below, including those about other Rules' Events, supports every comparison and every arithmetic operation.

### Comparisons

```java
temp.above(80)              // temp > 80, strictly
temp.below(-5)              // temp < -5, strictly
temp.between(18, 24)        // 18 <= temp <= 24, both bounds included
```

`above` and `below` also compare two Expressions, so a threshold can itself be a Channel:

```java
weight.above(ratedLoad)                   // overload, with a per-crane rated load
level.below(setpoint.minus(0.5))
```

To test "at least" or "at most", negate the opposite: `temp.below(80).not()` is `temp >= 80`.

### Arithmetic

Expressions support `plus`, `minus`, `times` and `dividedBy`, each with another Expression or a number, and `abs()`:

```java
supplyTemp.minus(returnTemp).above(5)     // temperature spread
current.times(voltage).above(2_000)       // power from two Channels
setpoint.minus(actual).abs().above(2)     // deviation in either direction
errors.dividedBy(requests).above(0.01)    // error rate above 1 %
tempF.minus(32).times(5.0 / 9).above(30)  // unit conversion
```

A division by zero, or any other operation without a finite result, is undefined.

### Trends: `rising`, `falling`, `rate`

A threshold tells you *where* a signal is. Often it matters more *where it is going*: a bearing at 60 °C is fine, but not if it has been warming up for ten minutes straight; a tank at 80 % is fine, but not if it is emptying at a litre per second. Trend functions describe how a Channel changes, so Rules can react before a threshold is reached, or distinguish a signal that is recovering from one that is getting worse.

**`rising()` and `falling()`** are Conditions: `rising()` holds when the Channel's latest value is greater than its previous one, `falling()` when it is less. An unchanged value is neither. A single step up says little in a noisy signal, so these are mostly used in one of three ways.

*A sustained trend*, with `forSamples`. This Rule detects a temperature that has risen at five consecutive Samples, even while it is still far below any alarm threshold:

```java
Rule warming = Rule.named("Warming").when(temp.rising()).forSamples(5);
```

```java
// temp:   40  41  42  42  43  44  45  46  47  45
// time:    0   1   2   3   4   5   6   7   8   9  (s)
// The plateau at 42 breaks the first run. The second run starts at 4 s and
// is Confirmed at 8 s, its fifth rising Sample: Warming 4000..9000
```

*Level plus direction*, with `and`. A high value that is still climbing needs action; a high value that is already falling is recovering:

```java
Rule pressureEscalating = Rule.named("PressureEscalating")
    .when(pressure.above(8).and(pressure.rising()));
```

*Peaks and troughs.* An Event of `rising()` lasts exactly as long as a climb. It Ends at the first Sample that is not higher, and its `max` Aggregate is the peak value:

```java
Rule climb = Rule.named("Climb").when(x.rising());
// x: 1 2 5 9 7 3 1 at 0..6 s  ->  Climb 1000..4000, e.aggregates(x).max() is 9.0
```

**`rate()`** is an Expression: how fast the Channel changes, in its own units per second. It is computed from the two latest values and their timestamps, `(latest - previous) / seconds between them`, so irregular sampling is handled correctly. Unlike `rising()`, it says *how fast*, so you compare it with a limit:

```java
// hard braking: speed in m/s drops by more than 4 m/s per second
Rule hardBraking = Rule.named("HardBraking").when(speed.rate().below(-4));

// leak: the tank level falls faster than any consumer can draw, for a whole minute
Rule leak = Rule.named("Leak").when(level.rate().below(-0.5)).forDuration(60_000);

// heat treatment: heating faster than 2 °C per minute (rate is per second)
Rule rampTooSteep = Rule.named("RampTooSteep").when(furnaceTemp.rate().above(2.0 / 60));
```

Because `rate()` looks at only two values, noise in the signal shows up in full in the rate. For noisy signals, take the rate of a [moving average](#moving-averages) instead: `level.averageOverSamples(4).rate()`.

All three are undefined until the Channel has two values. They use the Channel's own values, not Samples: if `pressure` is missing from a Sample, `rising()` still compares its two latest reported values, and `rate()` divides by the time between them.

A fourth function about a Channel's history, **`age()`**, is the time since the Channel last reported. It detects sensors that went silent and is explained with [advancing time](#silent-sensors-advancing-time).

### Moving averages

Raw sensor data is noisy. A single outlier crosses a threshold, and a rate computed from two jittery values jumps around. A moving average replaces each value with the mean of the most recent ones, so Rules see the underlying trend instead of the noise. It is also how you compare "now" with "normally": a value against its own average over the last hour.

```java
level.averageOverSamples(5)                          // mean of the last 5 values
level.averageOverDuration(Duration.ofMinutes(10))    // mean of the last 10 minutes
```

`averageOverSamples(n)` averages the last `n` values and is defined once `n` values exist. Use it for regularly sampled data. `averageOverDuration(ms)` averages the values from the last `ms` milliseconds before the latest value and is defined once the values span that long. Use it when Samples arrive irregularly, or when the window has a meaning in time ("the last 10 minutes").

A longer window smooths more but reacts later. Compare with the raw value to find sudden deviations from the recent normal:

```java
// a reading 20 % above its own 1-hour average
power.above(power.averageOverDuration(Duration.ofHours(1)).times(1.2))
```

A moving average supports everything a Channel does, including another average, `rising`, `falling`, `rate` and `age`. A smoothed slope, for example, is:

```java
level.averageOverSamples(4).rate().above(0.2)
```

Two averages of the same Channel make a classic crossover signal:

```java
Rule goldenCross = Rule.named("GoldenCross").when(
    price.averageOverSamples(50).above(price.averageOverSamples(200)));
```

While an average warms up it is undefined, so Events of such a Rule that start right after the warm-up have `truncatedStart()`: the Rule could not tell whether the Condition held earlier.

### Logic: `and`, `or`, `not`

Single comparisons rarely describe a real situation. Combine them:

- **`and`**: all criteria must hold. It makes a Rule specific: "hot" alone may be normal under load, "hot while the fan is off" is a cooling failure.
- **`or`**: any criterion suffices. It merges several ways into the same situation, such as any of several doors being open.
- **`not`**: the opposite. It expresses "outside a band", "at least" (`below(80).not()` is `>= 80`) and "no pulse now" for [Pulse Channels](#pulse-channels).

```java
temp.above(80).and(fan.below(0.5))       // cooling failure
doorA.above(0.5).or(doorB.above(0.5))     // any door open
temp.between(18, 24).not()                // outside the comfort band
```

There is no operator precedence to remember: calls nest explicitly. This holds when the machine runs and at least one of the two warning signs is present:

```java
rpm.above(100).and(vibration.above(4).or(temp.above(80)))
```

### Undefined values

A Condition is undefined when the data it needs does not exist yet: a Channel without a value so far, a moving average still warming up, `rate` before two values, a division by zero, or `latest(...)` before the first such Event. Undefined **never counts as true**, and that includes `not()`:

```java
pressure.above(5).not()   // false as long as pressure has no value, not true
```

So a Rule never fires because data is missing. To detect missing data explicitly, use [`age()`](#silent-sensors-advancing-time).

### Reading an Expression's value

To log, plot or check the values your Rules compare, register the Expressions with `watch` and read them with `value`:

```java
ChannelExpression smoothTemp = temp.averageOverSamples(5);
Detector detector = Detector.builder()
    .rules(overheat)
    .watch(smoothTemp)
    .build();

detector.push(0, Map.of(temp, 82.0));
detector.value(smoothTemp);    // OptionalDouble.empty: the average is still warming up
// ... four more Samples later
detector.value(smoothTemp).ifPresent(v -> System.out.println("smoothed: " + v));
```

`value` reports the Expression at the latest Sample or time advance and is empty while the value is undefined. It may be called at any time, also from an EventListener callback (the moment being processed) and after `close()`. A watched Expression need not be used by any Rule. Reading only looks the value up, so watching costs nothing on Samples where you do not read. Reading an Expression that was not watched throws `IllegalArgumentException`; a Channel matches by name, every other Expression is the same object you passed to `watch`.

## Referring to other Rules

Rules can refer to the Events of Rules added before them, by passing the Rule itself. This composes simple Rules into sequences, combinations, counts and durations.

### `during`: while another Event runs

`during(a)` holds while an A Event is Confirmed and has not Ended. It turns an Event into a state that other Rules can use. Two uses are common.

*Restricting a Rule to a phase.* Many criteria only make sense in a certain operating state. Vibration of a stopped machine is irrelevant, and a Rule for it would fire on every truck driving past. Define the phase once as a Rule and refer to it:

```java
Rule running = Rule.named("Running").when(rpm.above(100)).forDuration(10_000);
Rule roughRunning = Rule.named("RoughRunning").when(during(running).and(vibration.above(4)));
```

`RoughRunning` Ends at the latest when `Running` Ends. The phase Rule brings its own timing, here ten seconds of start-up during which vibration is ignored, which a plain `rpm.above(100)` could not express.

*Combining Events that have their own timing.* Each part keeps its own duration, End Condition and hysteresis, and the combination holds while both are Confirmed:

```java
Rule tachycardia = Rule.named("Tachycardia").when(hr.above(100)).forDuration(30_000);
Rule desaturation = Rule.named("Desaturation").when(spo2.below(90)).forDuration(10_000);
Rule hypoxicTachycardia = Rule.named("HypoxicTachycardia")
    .when(during(tachycardia).and(during(desaturation)));
```

### `after`: sequences

`after(a)` holds once an A Event has Ended. On its own this stays true forever, so it is narrowed:

- `.within(ms)`: the most recent A Event Ended at most `ms` ago.
- `.unless(b, c, ...)`: no B or C Event has been Confirmed since the most recent A Event Ended.

```java
Rule doorOpen = Rule.named("DoorOpen").when(door.above(0.5));
Rule excursionAfterDoor = Rule.named("ExcursionAfterDoor")
    .when(after(doorOpen).within(Duration.ofMinutes(30)).and(temp.above(8)));
```

```java
Rule lift = Rule.named("Lift").when(weight.above(500));
Rule maintenance = Rule.named("Maintenance").when(serviceMode.above(0.5));
Rule liftSinceService = Rule.named("LiftSinceService").when(after(lift).unless(maintenance));
```

Interrupting Events are named explicitly with `unless` rather than implied, so a sequence means exactly what it says. [ADR-0002](docs/adr/0002-explicit-unless-for-sequences.md) explains why.

### `count`: how many Events

`count(a)` is the number of A Events Confirmed so far, an Expression like any other. `.within(ms)` counts only those Confirmed in a sliding window ending now:

```java
Rule run = Rule.named("Run").when(power.above(500)).forDuration(30_000);
Rule frequentStarts = Rule.named("FrequentStarts")
    .when(count(run).within(Duration.ofHours(1)).above(3));
Rule serviceDue = Rule.named("ServiceDue").when(count(run).above(10_000));
```

An Event counts from the moment it is Confirmed, so a running Event is included.

### `totalDuration`: time spent in Events

`totalDuration(a)` is the summed duration in milliseconds of all A Events: the Ended ones in full, plus a running one up to now. It grows continuously while an A Event runs, so a limit is detected the moment it is crossed:

```java
Rule excursion = Rule.named("Excursion").when(temp.above(8));
Rule cumulativeLimit = Rule.named("CumulativeLimit")
    .when(totalDuration(excursion).above(45 * 60_000));
```

### `latest`: values of the most recent Event

`latest(a)` gives access to the most recent Confirmed A Event, whether still running or Ended:

- `.min(x)`, `.max(x)`, `.mean(x)`: its [Aggregates](#aggregates) of Channel `x`, so far if it is running. Rule A aggregates `x` for this automatically.
- `.duration()`: its duration in ms, up to now if it is running.

Each is an Expression, undefined while there has been no A Event. This relates the present to an earlier Event, for example a partial set-down of a lifted load:

```java
Rule loadLifted = Rule.named("LoadLifted").when(weight.above(500)).endWhen(weight.below(50));
Rule partialSetDown = Rule.named("PartialSetDown")
    .when(during(loadLifted).and(weight.below(latest(loadLifted).max(weight).times(0.5))));
```

or a machine cycle that is taking too long, detected while it is still running:

```java
Rule cycle = Rule.named("Cycle").when(spindle.above(0.5));
Rule slowCycle = Rule.named("SlowCycle")
    .when(during(cycle).and(latest(cycle).duration().above(120_000)));
```

## Aggregates

Every Event carries Aggregates of the Channels its Rule's Conditions use, plus those requested with [`aggregate(...)`](#aggregates-for-more-channels-aggregate). Read them with `event.aggregates(x)`:

```java
@Override public void onEnded(Event e) {
  Aggregates s = e.aggregates(speed);
  System.out.printf("avg %.1f m/s, max %.1f m/s, %.0f m driven%n",
      s.timeWeightedMean(), s.max(), s.integral());
}
```

| Component            | Meaning                                                                                            |
| -------------------- | -------------------------------------------------------------------------------------------------- |
| `min`, `max`, `mean` | Over the Samples of the Event in which the Channel had a value.                                    |
| `count`              | The number of those Samples. If it is 0, `min`, `max` and `mean` are NaN.                          |
| `timeWeightedMean`   | The mean with each value weighted by how long it held: until the next Sample, time advance or the Event's end. |
| `integral`           | Value x seconds, time-weighted like the mean: distance from speed, energy from power, dose from rate. |

The plain `mean` treats every Sample alike, which is right for regularly sampled data. With irregular sampling, for example a sensor that reports more often when something happens, `timeWeightedMean` gives the true average over time. The time-weighted values are NaN if the Channel had no value during the Event, and the time-weighted mean also for an Event of zero length.

Aggregates in the Pending and Confirmed snapshots cover the Event so far. Reading a Channel the Rule does not aggregate throws an `IllegalArgumentException` that names the missing `.aggregate(...)`, so a forgotten declaration is never mistaken for "no data".

## Batch processing and sampling rates

For a recorded series, `detect` runs all Samples through a fresh Detector, closes it and returns every Ended Event, in the order they Ended:

```java
Rule loadLifted = Rule.named("LoadLifted").when(weight.above(500));
Detector detector = Detector.builder()
    .rules(loadLifted)
    .build();

List<Event> events = detector.detect(List.of(
    new Sample(0,     Map.of(weight, 0.0)),
    new Sample(500,   Map.of(weight, 1200.0)),
    new Sample(1_000, Map.of(weight, 0.0))));   // [LoadLifted 500..1000]
```

`detect` accepts any `Iterable<Sample>`, so a lazily read file works as well. A listener, if configured, is still called. A Detector can only be used once: `detect` on a Detector that has already seen Samples throws.

**Sampling rate.** For regularly sampled data without timestamps, declare the rate on the builder and push only values. The timestamp of Sample `i` (from 0) is `round(i * 1000 / hz)`:

```java
Rule spike = Rule.named("Spike").when(accel.above(3)).forSamples(5);
Detector detector = Detector.builder()
    .samplingRate(50)    // 50 Hz: Samples are 20 ms apart
    .rules(spike)
    .build();

detector.push(Map.of(accel, 0.1));                     // timestamp 0
detector.push(Map.of(accel, 3.2));                     // timestamp 20
// or, for a whole recording:
List<Event> events = detector.detectValues(listOfMaps);
```

The rate may be at most 1000 Hz, since timestamps have millisecond resolution. With a sampling rate, `push(timestamp, values)` and `advanceTo` throw, because time advances with the Samples; without one, `push(values)` throws.

## Silent sensors: advancing time

A Detector only evaluates Rules when something happens. If Samples stop arriving, nothing happens: a duration is never reached, and a sensor that fell silent goes unnoticed. `advanceTo(timestamp)` tells the Detector that time has passed without new data, typically driven by a timer:

```java
Rule sensorLost = Rule.named("SensorLost").when(hr.age().above(10_000));
Detector detector = Detector.builder()
    .rules(sensorLost)
    .build();

detector.push(0, Map.of(hr, 72.0));
detector.advanceTo(5_000);    // hr is 5 s old
detector.advanceTo(12_000);   // hr is 12 s old: SensorLost Confirmed
detector.push(13_000, Map.of(hr, 70.0));   // SensorLost Ended at 13000
```

`x.age()` is the number of milliseconds since `x` last had a value, measured at the current Sample or advance. It is undefined before the first value.

At an advance, all Rules are evaluated with every Channel holding its value: durations can be reached, `totalDuration` and `count(...).within(...)` move on, and `maxGap` is noticed. An advance is not a Sample, though: it does not count for `forSamples`, nor for the Sample-based Aggregates `min`, `max`, `mean` and `count`. The time-weighted Aggregates do include the time up to it.

## Pulse Channels

Some Channels report one-off occurrences rather than a state: a deployment, a button press, a trade. By default a Channel keeps its last value, so `deploy = 1` would stay 1 forever. Create such Channels as Pulse Channels:

```java
Channel deploy = Channel.pulse("deploy");
Channel errors = Channel.named("errors");

Rule deployment = Rule.named("Deployment").when(deploy.above(0.5));
Rule regression = Rule.named("Regression")
    .when(after(deployment).within(30 * 60_000).and(errors.above(5)));
Detector detector = Detector.builder()
    .rules(deployment, regression)
    .build();

detector.push(0,      Map.of(errors, 1.0));
detector.push(60_000, Map.of(errors, 1.0, deploy, 1.0));  // Deployment starts
detector.push(120_000, Map.of(errors, 1.0));                // deploy is absent: Deployment Ended
```

A Pulse Channel's value exists only in the Sample that carries it. In every other Sample and at every advance it is *absent*. Absent is not undefined, because the absence of a pulse is known:

- Comparisons, `rising` and `falling` on an absent pulse are **false**, so `not()` of them is **true**: `deploy.above(0.5).not()` means "no deployment now".
- Arithmetic on an absent pulse is undefined.
- `age()` is the time since the last pulse, e.g. `heartbeat.age().above(60_000)`.

## Restarts: History

A Detector keeps its state in memory. After a restart, Rules like `after(maintenance)` would have forgotten the last maintenance. Pass Events stored before the restart as History:

```java
// before the restart: store Ended Events, e.g. in onEnded
List<Event> stored = loadFromDatabase();

Rule maintenance = Rule.named("Maintenance").when(serviceMode.above(0.5));
Rule overdueService = Rule.named("OverdueService")
    .when(after(maintenance).within(Duration.ofDays(90)).not());
Detector detector = Detector.builder()
    .rules(maintenance, overdueService)
    .history(stored)
    .build();
```

History Events are matched to Rules by `name()`, so each must belong to a Rule added to the Detector. They must be Confirmed and Ended (both `confirmed()` and `end()` present), and the first Sample must come after the latest of their ends. `after`, `unless`, `count`, `totalDuration` and `latest(...).duration()` take them into account. They carry no Aggregates for `latest(...).min/max/mean`, and they are not reported to the listener again.

## Errors and validation

Mistakes are reported as early as possible, most of them when the Detector is built:

| Mistake                                                               | Reported by                         |
| --------------------------------------------------------------------- | ----------------------------------- |
| An empty Rule name                                                    | `Rule.named(...)`: `IllegalArgumentException` |
| A Rule without `when`, or two Rules with the same name                | `rules(...)`: `IllegalArgumentException` |
| `forDuration`/`forSamples` before any `when`/`endWhen`                | `IllegalStateException`             |
| Negative durations, `forSamples(0)`, `between` with `low > high`      | `IllegalArgumentException`          |
| A sampling rate outside (0, 1000] Hz                                  | `samplingRate(...)`: `IllegalArgumentException` |
| A History Event that has not Ended                                    | `history(...)`: `IllegalArgumentException` |
| A Rule referring to itself, to a Rule added after it or not added     | `build()`: `IllegalArgumentException` |
| A History Event whose name matches no added Rule                      | `build()`: `IllegalArgumentException` |
| One Channel name used both as a Pulse Channel and as an ordinary one  | `build()`: `IllegalArgumentException` |
| An empty Channel name                                                 | `Channel.named`/`Channel.pulse`: `IllegalArgumentException` |
| A timestamp not after the previous one or the History's latest end    | `push`/`advanceTo`: `IllegalArgumentException` |
| A NaN, infinite or null value                                         | `push`: `IllegalArgumentException`, and the Sample is ignored |
| Pushing after `close()`, or the wrong `push` for the sampling mode    | `IllegalStateException`             |
| Reading Aggregates of a Channel the Rule does not aggregate           | `Event.aggregates(...)`: `IllegalArgumentException` |

## Examples

Runnable end-to-end examples from different domains, each a test with its expected Events:

| Domain | Example | Shows |
| --- | --- | --- |
| Cranes | [CraneScenarioTest](src/test/java/io/github/romanhaendler/rbed/CraneScenarioTest.java) | hysteresis, `during`, classifying a load, partial set-down via `latest` |
| Manufacturing | [MachineToolOverheatExample](src/test/java/io/github/romanhaendler/rbed/examples/MachineToolOverheatExample.java) | sampling-rate mode, `rate`, combined `during`s |
| Building services | [HvacShortCyclingExample](src/test/java/io/github/romanhaendler/rbed/examples/HvacShortCyclingExample.java) | `after().within()`, `count().within()` |
| Healthcare | [VitalSignsExample](src/test/java/io/github/romanhaendler/rbed/examples/VitalSignsExample.java) | `advanceTo` and `age()` for a silent sensor, `maxGap` |
| Automotive | [FleetTelematicsExample](src/test/java/io/github/romanhaendler/rbed/examples/FleetTelematicsExample.java) | deceleration via `rate`, distance via `integral` |
| Finance | [TradingSignalsExample](src/test/java/io/github/romanhaendler/rbed/examples/TradingSignalsExample.java) | moving-average crosses, truncated start after warm-up |
| Logistics | [ColdChainExample](src/test/java/io/github/romanhaendler/rbed/examples/ColdChainExample.java) | attributing a cause with `after`, `totalDuration` limit |
| Environment | [FloodWarningExample](src/test/java/io/github/romanhaendler/rbed/examples/FloodWarningExample.java) | smoothed rate of a moving average, hysteresis |
| Sports | [IntervalTrainingExample](src/test/java/io/github/romanhaendler/rbed/examples/IntervalTrainingExample.java) | `after`, rep count via `count`, listener-side timing |
| IT operations | [DeploymentRegressionExample](src/test/java/io/github/romanhaendler/rbed/examples/DeploymentRegressionExample.java) | a Pulse Channel reporting deployments |

## Performance

Conditions are bound to the Detector once, in `build()`. After that, Rules read Channel state directly, and pushing a Sample costs one hash lookup per value. In steady state (no Event starting or ending) it allocates nothing, so memory stays bounded however long an Event lasts. Moving averages use fixed ring buffers with compensated sums, so they neither allocate nor drift.

Measured with `mvn test -Pperformance` (Apple M2, JDK 21):

| Scenario                                                  | Throughput             |
| --------------------------------------------------------- | ---------------------- |
| One threshold Rule, Events every 100 Samples              | ~40-45 million Samples/s |
| Realistic Rule set: 11 Rules using every feature           | ~5.7 million Samples/s |
| 50 Rules x 20 Channels, Events every 40 Samples           | ~1.2-1.5 million Samples/s |

A running Event keeps Aggregates only for the Channels its Rule uses (plus those requested with `.aggregate(...)`), at about 1 ns per Channel per Sample.

The performance tests are excluded from the default build because timings depend on the machine. The allocation checks among them are deterministic.

## Memory

Values of Channels that no Rule uses are ignored and leave no trace. What a Detector holds is known from its Rules:

| Feature                         | Retains                                       |
| ------------------------------- | --------------------------------------------- |
| Channel used by a Rule          | 1 value                                       |
| `averageOverSamples(n)`         | n values; `build()` rejects n above the Retention Limit |
| `averageOverDuration(w)`        | every value within `w`, at most the Retention Limit |
| `count(...).within(w)`          | one timestamp per Confirmed Event within `w`, at most the Retention Limit |
| everything else                 | constant per Rule                             |

Once the Retention Limit drops timestamps, `count(...).within(...)` yields min(true count, limit), so a Condition with a threshold at or above the limit (e.g. `.above(limit)`) can never hold; `droppedValues()` and the logged warning signal this. The timestamp buffer keeps its largest size after a burst (at most the limit).

`detector.retainedValues()` returns the number of entries currently held (Channels, moving-average values and count timestamps). It is a plain counter read, so it is cheap to poll, for example as a metrics gauge.

The **Retention Limit** (default 1,000,000 entries, about 16 MB per window) caps every `averageOverDuration` window and every `count(...).within(...)` list of confirmation timestamps (History included); set it with `Detector.builder().retentionLimit(n)`. When a full window gets a new value, its oldest entry is dropped as if it had left the window by time, so the average is the mean of the entries that remain, and detection continues. A count is then a lower bound of at most the limit. The first drop logs one `WARNING` through `System.Logger` naming the Rule and the limit. `detector.droppedValues()` returns how many entries were dropped because of the limit (not those leaving by time), for example to alert on.

## Notes

- A Detector is single-use and not thread-safe. Use one per data stream, and feed it from one thread at a time.
- Rules and the listener are fixed when the Detector is built. To change Rules, build a new Detector and pass the Events so far as [History](#restarts-history).
- `EventListener` shares its simple name with `java.util.EventListener`. With `import java.util.*;`, import `io.github.romanhaendler.rbed.EventListener` explicitly.

## License

[MIT](LICENSE)
