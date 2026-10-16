# End Conditions and hysteresis

Real signals hover around their thresholds. A Rule that only has `when` Ends its Event the moment the Condition stops holding, so a weight fluctuating between 490 and 520 around a threshold of 500 produces a new Event every few Samples. An End Condition fixes this: once an Event is Confirmed, it continues until the End Condition holds, whatever `when` does.

## A separate end threshold

Give the End Condition a threshold below the start threshold, and the signal has to fall clearly before the Event Ends:

```java
Rule loadLifted = Rule.named("LoadLifted")
    .when(weight.above(500))
    .endWhen(weight.below(50));
```

The gap between the two thresholds is the hysteresis: noise that stays inside it starts no Event and Ends none.

## A swinging load

A crane's load cell reports the `weight` once a second. At 3 s it lifts a load that reads around 500 kg as it swings, at 14 s a deep swing reads 440 kg, and at 22 s the load is set down. `LoadLifted` starts above 500 kg; the End threshold starts out equal to it, which is no hysteresis at all.

<!-- scenario end-conditions -->

At an End threshold of 500 kg the Rule flaps: four Events, the first only one second long. Move the End threshold down to 450 kg and the noise around 500 no longer Ends anything: one Event from 4 s until the swing at 14 s, and a second one until the load is set down. At 440 kg or lower even the swing stays inside the hysteresis, and a single Event runs from 4 s to 22 s. The README's 50 kg Ends it at 23 s, the first Sample below 50.

Lower the End threshold to 0 kg and the End Condition never holds: the Event runs until `close()`, which Ends it at the last Sample with a Truncated end.

## When does the next Event start?

At an End threshold of 500 kg, the weight is above 500 again at 6 s and 7 s, yet no Event starts there. While an Event runs on its End Condition, `when` is not evaluated, and a new Event only starts once `when` has been seen not holding after the previous one Ended: 489 kg at 8 s does that, and the next Event starts at 10 s. Each Rule has at most one Event in progress, and this is why a Rule with an End Condition does not flicker.

## A duration for the End Condition

The End Condition can have its own duration, written right after it: `.endWhen(weight.below(450)).forDuration(1_000)` Ends the Event only once the weight has stayed below 450 for a second. Set the End threshold to 450 kg and the End duration to 1 s: the swing at 14 s lasts a single Sample, so it no longer Ends the Event, and the first Event now lasts until the load is set down.

The Event's `end()` is where the End Condition *started* holding, 22 s here, not where its duration was reached, so the Event's extent is what you would draw by hand. The listener's `onEnded` comes later, once the duration is reached: at 23 s with an End duration of 1 s, at 24 s with 2 s. Until then the Event is still Confirmed, and `during(...)` of it still holds.

## Reference

- [End Conditions and hysteresis](https://github.com/romanhaendler/rbed#end-conditions-and-hysteresis) and [Durations: forDuration and forSamples](https://github.com/romanhaendler/rbed#durations-forduration-and-forsamples) in the README, the canonical text
- [Rule](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html) in the Javadoc
