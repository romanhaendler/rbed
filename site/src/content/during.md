# During another Event

A Rule can refer to the Events of another Rule by passing that Rule itself. `during(a)` holds while an Event of Rule `a` is Confirmed and has not Ended. It turns an Event into a state that other Rules can use.

A Rule may only refer to Rules added before it with `rules(...)`, so it sees their state at the same Sample, and dependencies can never be circular. Since the reference is the Rule object, not a name, a misspelled reference does not compile, and `build()` rejects a reference to a Rule that was not added or is added later.

## Restricting a Rule to a phase

Many criteria only make sense in a certain operating state. Vibration of a stopped machine is irrelevant, and a Rule for it would fire on every truck driving past. Define the phase once as a Rule and refer to it:

```java
Rule running = Rule.named("Running").when(rpm.above(100)).forDuration(10_000);
Rule roughRunning = Rule.named("RoughRunning").when(during(running).and(vibration.above(4)));
```

`RoughRunning` Ends at the latest when `Running` Ends. The phase Rule brings its own timing, here ten seconds of start-up during which vibration is ignored, which a plain `rpm.above(100)` could not express.

## Combining Events with their own timing

Each part keeps its own duration, End Condition and hysteresis, and the combination holds while both are Confirmed. A patient's heart rate `hr` is above 100 from 40 s to 200 s, and once more from 230 s to 250 s. Their oxygen saturation `spo2` dips below 90 for one Sample at 20 s, from 100 s to 160 s, and from 230 s to 260 s. `Tachycardia` needs 30 seconds, `Desaturation` 10, and `HypoxicTachycardia` holds while both are Confirmed. The three lanes share the time axis, so you can see the third band start and end where the other two overlap.

<!-- scenario during -->

At the default durations:

- `Tachycardia` goes Pending at 40 s and is Confirmed at 70 s. `Desaturation` goes Pending at 100 s and is Confirmed at 110 s, and so is `HypoxicTachycardia`: a Pending Event does not count for `during`.
- `HypoxicTachycardia` Ends at 160 s with `Desaturation`, although `Tachycardia` runs until 200 s. A combination Ends at the latest when one of its parts Ends.
- The dip at 20 s is too short for `Desaturation` and is Discarded.
- At 230 s both go Pending again. `Desaturation` is Confirmed at 240 s, but the heart rate falls at 250 s, before its 30 seconds are reached: `Tachycardia` is Discarded, and there is no `HypoxicTachycardia`.

Shorten `Tachycardia`'s duration to 15 s and it is Confirmed at 245 s, so `HypoxicTachycardia` holds from 245 s until `Tachycardia` Ends at 250 s. Set `Desaturation`'s duration to 0 s and the combination starts with the dip at 100 s.

The [Healthcare](../healthcare/) example uses these Rules on a longer recording.

## Reference

- [during: while another Event runs](https://github.com/romanhaendler/rbed#during-while-another-event-runs) and [Rule order](https://github.com/romanhaendler/rbed#rule-order) in the README, the canonical text
- [Conditions](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html) in the Javadoc
