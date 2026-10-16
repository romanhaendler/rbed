# Data gaps

A Channel keeps its last value until the next Sample brings a new one. If a sensor stops reporting, that last value would hold forever, and an Event could run on stale data. `maxGap` bounds how far apart two Samples may be.

## maxGap

```java
Rule running = Rule.named("Running").when(rpm.above(100)).maxGap(Duration.ofSeconds(10));
```

When the next Sample (or [time advance](https://github.com/romanhaendler/rbed#silent-sensors-advancing-time)) comes more than 10 seconds after the previous Sample:

- a Pending Event is Discarded;
- a Confirmed Event Ends with a Truncated end, at the last moment evaluated before the gap;
- an Event that starts right after the gap has a Truncated start, since its Rule has not seen its Conditions not holding since data resumed.

Without `maxGap`, gaps of any length are accepted.

## A silent sensor

A machine reports its `rpm` once a second and runs from 6 s to 31 s. For six seconds its sensor is silent: the Samples inside the gap are missing, which shows as a stretch of the line without dots. `Running` is an Event while `rpm` is above 100. Move the gap and change `maxGap`, and watch whether the Event is Ended at the gap or kept across it. A halo on a band's edge marks a Truncated start or end.

<!-- scenario data-gaps -->

By default the gap runs from 12 s to 18 s, one second longer than the `maxGap` of 5 s. `Running` Ends at 12 s, the last Sample before the gap, with a Truncated end: nobody knows whether the machine kept running. A second Event starts at 18 s with a Truncated start, since nobody knows when it started either.

Raise `maxGap` to 6 s and the gap is no longer too long: the Samples at 12 s and 18 s are exactly 6 seconds apart, not more, and a single, complete Event runs from 6 s to 32 s.

Move the gap to 33 s, after the machine has stopped, and it changes nothing. Move it to 2 s, before the machine starts: the Event starts at 8 s, the first Sample after the gap, and its start is Truncated although `rpm` was 0 before the gap. After a gap, the Rule has to see its Conditions not holding again before it knows where an Event begins.

## Reference

- [Data gaps: maxGap](https://github.com/romanhaendler/rbed#data-gaps-maxgap) and [The Event lifecycle](https://github.com/romanhaendler/rbed#the-event-lifecycle) (Truncation) in the README, the canonical text
- [Rule](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html) and [Event](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html) in the Javadoc

Methods used: [`when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`maxGap`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#maxGap%28java.time.Duration%29), [`push`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`close`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
