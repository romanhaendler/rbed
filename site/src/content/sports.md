# Sports

A runner's speed and heart rate are recorded at 1 Hz during an interval session: after a one-minute warm-up, three reps of 30 s sprint at 7 m/s and 90 s jog. The heart rate climbs by 1 bpm/s from 140 while sprinting and drops by 1 bpm/s while jogging.

<!-- scenario sports -->

## The Rules

- **Sprint**: faster than 6 m/s for 5 s, over below 4 m/s.
- **Recovered**: the heart rate below 120 within 2 minutes after a Sprint. A listener computes the recovery time, from the end of a Sprint to Recovered, shown as a note in the table: 51 s after each rep. The last one still holds when the data ends, so its end is Truncated.
- **SessionDone**: more than 2 Sprints, that is, the third Sprint was Confirmed, at 305 s.

## Source and reference

The example is [IntervalTrainingExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/IntervalTrainingExample.java), listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples) with its expected Events.

Methods used: [`Rule.when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`forDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`endWhen`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`below`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`and`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`after`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28long%29), [`count`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#count%28io.github.romanhaendler.rbed.Rule%29), [`EventListener.onConfirmed`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onConfirmed%28io.github.romanhaendler.rbed.Event%29), [`EventListener.onEnded`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onEnded%28io.github.romanhaendler.rbed.Event%29), [`Event.is`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#is%28io.github.romanhaendler.rbed.Rule%29).

The concepts at work, each on a page of its own: [After another Event](../after/), [Counting Events](../count/), [End Conditions and hysteresis](../end-conditions/).
