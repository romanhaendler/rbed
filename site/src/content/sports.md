# Sports

A runner's speed and heart rate are recorded at 1 Hz during an interval session: after a one-minute warm-up, three reps of 30 s sprint at 7 m/s and 90 s jog. The heart rate climbs by 1 bpm/s from 140 while sprinting and drops by 1 bpm/s while jogging.

<!-- scenario sports -->

## The Rules

- **Sprint**: faster than 6 m/s for 5 s, over below 4 m/s.
- **Recovered**: the heart rate below 120 within 2 minutes after a Sprint. A listener computes the recovery time, from the end of a Sprint to Recovered, shown as a note in the table: 51 s after each rep. The last one still holds when the data ends, so its end is Truncated.
- **SessionDone**: more than 2 Sprints, that is, the third Sprint was Confirmed, at 305 s.

### Things to try

- Raise *Recovered below* to 140 bpm: the heart rate falls below it sooner, and each recovery takes 31 s instead of 51 s.

## Reference

The example is [IntervalTrainingExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/IntervalTrainingExample.java), a test with its expected Events, listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples).

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Rule.endWhen`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`Expression.below`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`Conditions.after`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28java.time.Duration%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Conditions.count`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#count%28io.github.romanhaendler.rbed.Rule%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.listener`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`EventListener.onEnded`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onEnded%28io.github.romanhaendler.rbed.Event%29), [`Event.is`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#is%28io.github.romanhaendler.rbed.Rule%29), [`Event.end`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#end%28%29), [`EventListener.onConfirmed`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onConfirmed%28io.github.romanhaendler.rbed.Event%29), [`Event.start`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#start%28%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).

The concepts at work, each on a page of its own: [After another Event](../after/), [Counting Events](../count/), [End Conditions and hysteresis](../end-conditions/).
