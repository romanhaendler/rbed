# Counting Events

`count(a)` is the number of Events of Rule `a` Confirmed so far, an Expression like any other. `.within(ms)` counts only those Confirmed in a sliding window ending now:

```java
Rule run = Rule.named("Run").when(power.above(500)).forDuration(30_000);
Rule frequentStarts = Rule.named("FrequentStarts")
    .when(count(run).within(Duration.ofHours(1)).above(3));
Rule serviceDue = Rule.named("ServiceDue").when(count(run).above(10_000));
```

An Event counts from the moment it is Confirmed, so a running Event is included.

## Frequent starts

A machine's power is sampled once a minute. It runs for five minutes from minutes 10, 30, 45, 60, 150 and 200. A `Run` needs 30 seconds above 500 W, so each one is Confirmed at the next Sample, a minute after its start. `FrequentStarts` holds while more than the limit of Runs were Confirmed within the window. The chart under the Events plots `count(run).within(...)` at every Sample, as rbed computes it, with the limit as a line.

<!-- scenario count -->

With an hour's window and a limit of 3:

- The count steps up at every Confirmed mark of a `Run`: 1 at minute 11, 2 at 31, 3 at 46.
- At minute 61 the fourth `Run` is Confirmed, while it is still running. The count is 4, more than 3, and `FrequentStarts` starts.
- At minute 72 the `Run` Confirmed at minute 11 lies more than an hour back and drops out of the window. The count falls to 3, and `FrequentStarts` Ends.
- The later Runs are too far apart to count up again.

`above(3)` means more than 3, so it takes the fourth start. Narrow the window to 30 minutes and no four starts fall into it: `FrequentStarts` never holds. Without `within`, as in `ServiceDue`, the count only grows.

The [Building services](../building-services/) example counts a compressor's starts the same way.

## Reference

- [count: how many Events](https://github.com/romanhaendler/rbed#count-how-many-events) in the README, the canonical text
- [EventCount](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventCount.html) in the Javadoc

Methods used: [`when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`forDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`count`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#count%28io.github.romanhaendler.rbed.Rule%29), [`within`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventCount.html#within%28java.time.Duration%29), [`push`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29).
