# Time spent in Events

`totalDuration(a)` is the summed duration in milliseconds of all Events of Rule `a`: the Ended ones in full, plus a running one up to now. It grows continuously while an Event of `a` runs, so a limit is detected the moment it is crossed:

```java
Rule excursion = Rule.named("Excursion").when(temp.above(8));
Rule cumulativeLimit = Rule.named("CumulativeLimit")
    .when(totalDuration(excursion).above(Duration.ofMinutes(45).toMillis()));
```

## A cumulative limit

A cold chain logs its temperature once a minute. It is above 8 °C from minute 20 to 40, from 80 to 95 and from 150 to 175: 20, 15 and 25 minutes, an hour in all. A single excursion is tolerable, but not more than the limit in total.

<!-- live -->
The chart plots `totalDuration(excursion)` at every Sample, as rbed computes it, in minutes like the limit.

<!-- scenario total-duration draw -->

`totalDuration(excursion)` rises while an `Excursion` runs and stays level between them: 20 minutes after the first, 35 after the second. At the default limit of 45 minutes, the sum reaches 45 at minute 160, in the middle of the third excursion, and exceeds it at minute 161, where `CumulativeLimit` starts. It never Ends, since the sum never decreases: it runs to the end of the data, and `close()` gives it a Truncated end.

### Things to try

- Raise the limit to 60 minutes: the sum reaches it at minute 175 but never exceeds it, so there is no `CumulativeLimit`.
- Lower it to 30 minutes: the sum exceeds it during the second excursion, and `CumulativeLimit` starts at minute 91.

## Reference

- [totalDuration: time spent in Events](https://github.com/romanhaendler/rbed#totalduration-time-spent-in-events) in the README, the canonical text
- [Conditions](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html) in the Javadoc

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Conditions.totalDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#totalDuration%28io.github.romanhaendler.rbed.Rule%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
