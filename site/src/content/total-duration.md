# Time spent in Events

`totalDuration(a)` is the summed duration in milliseconds of all Events of Rule `a`: the Ended ones in full, plus a running one up to now. It grows continuously while an Event of `a` runs, so a limit is detected the moment it is crossed:

```java
Rule excursion = Rule.named("Excursion").when(temp.above(8));
Rule cumulativeLimit = Rule.named("CumulativeLimit")
    .when(totalDuration(excursion).above(45 * 60_000));
```

## A cumulative limit

A cold chain logs its temperature once a minute. It is above 8 °C from minute 20 to 40, from 80 to 95 and from 150 to 175: 20, 15 and 25 minutes, an hour in all. A single excursion is tolerable, but not more than the limit in total. The chart under the Events plots `totalDuration(excursion)` at every Sample, as rbed computes it, in minutes like the limit.

<!-- scenario total-duration -->

The line rises while an `Excursion` runs and stays level between them: 20 minutes after the first, 35 after the second. With the default limit of 45 minutes, the sum reaches 45 at minute 160, in the middle of the third excursion, and exceeds it at minute 161, where `CumulativeLimit` starts. It never Ends, since the sum never decreases: it runs to the end of the data, and `close()` gives it a Truncated end.

Raise the limit to 60 minutes and the sum reaches it at minute 175 but never exceeds it, so there is no `CumulativeLimit`. Lower it and `CumulativeLimit` starts earlier.

## Reference

- [totalDuration: time spent in Events](https://github.com/romanhaendler/rbed#totalduration-time-spent-in-events) in the README, the canonical text
- [Conditions](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html) in the Javadoc

Methods used: [`when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`totalDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#totalDuration%28io.github.romanhaendler.rbed.Rule%29), [`push`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29).
