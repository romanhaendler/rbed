# Counting Events

`count(a)` is the number of Events of Rule `a` Confirmed so far, an Expression like any other. `.within(duration)` counts only those Confirmed in a sliding window ending now:

```java
Rule run = Rule.named("Run").when(power.above(500)).forDuration(Duration.ofSeconds(30));
Rule frequentStarts = Rule.named("FrequentStarts")
    .when(count(run).within(Duration.ofHours(1)).above(3));
Rule serviceDue = Rule.named("ServiceDue").when(count(run).above(10_000));
```

An Event counts from the moment it is Confirmed, so a running Event is included.

## Frequent starts

A machine's power is sampled once a minute. It runs for five minutes from minutes 10, 30, 45, 60, 150 and 200. A `Run` needs 30 seconds above 500 W, so each one is Confirmed at the next Sample, a minute after its start. `FrequentStarts` holds while more than the limit of Runs were Confirmed within the window. The chart under the Events plots `count(run).within(...)` at every Sample, as rbed computes it, with the limit as a line.

<!-- scenario count draw -->

At the defaults, an hour's window and a limit of 3:

- The count steps up at every Confirmed mark of a `Run`: 1 at minute 11, 2 at 31, 3 at 46.
- At minute 61 the fourth `Run` is Confirmed, while it is still running. The count is 4, more than 3, and `FrequentStarts` starts.
- At minute 72 the `Run` Confirmed at minute 11 lies more than an hour back and drops out of the window. The count falls to 3, and `FrequentStarts` Ends.
- The later Runs are too far apart to count up again.

`above(3)` means more than 3, so it takes the fourth start. Without `within`, as in `ServiceDue`, the count only grows.

### Things to try

- Narrow the window to 30 minutes: no four starts fall into it, and `FrequentStarts` never holds.
- Lower the limit to 2: the third `Run`, Confirmed at minute 46, is enough. `FrequentStarts` runs from 46 to 92, where the second `Run` drops out of the window.

The [Building services](../building-services/) example counts a compressor's starts the same way.

## Reference

- [count: how many Events](https://github.com/romanhaendler/rbed#count-how-many-events) in the README, the canonical text
- [EventCount](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventCount.html) in the Javadoc

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Conditions.count`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#count%28io.github.romanhaendler.rbed.Rule%29), [`EventCount.within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventCount.html#within%28java.time.Duration%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
