# After another Event

`after(a)` holds once an Event of Rule `a` has Ended. On its own this stays true forever, so it is narrowed:

- `.within(duration)`: the most recent Event of `a` Ended at most that long ago.
- `.unless(b, c, ...)`: no Event of `b` or `c` has been Confirmed since the most recent Event of `a` Ended.

Like every reference to another Rule, `a` must be added before the Rule that refers to it.

## A window after the door

A cold room's door is open from minute 10 to minute 12, and once more at minute 60. Its temperature is above 8 °C from minute 15 to minute 25, soon after the first opening, and from minute 100 to minute 110, long after the second. `ExcursionAfterDoor` is an excursion within a window after the door was open.

<!-- live -->
Move the window and watch which excursion still counts.

<!-- scenario after draw hide door -->

The window is measured from where `DoorOpen` Ended, not from where it started. At the default of 30 minutes, the first door opening Ended at minute 12, so the window lasts until minute 42 and covers the first excursion: `ExcursionAfterDoor` runs from 15 to 25. The second door opening Ended at minute 61, 39 minutes before the second excursion starts, so that excursion is not attributed to the door.

### Things to try

- Widen the window to 45 minutes: the second excursion counts too, but only until minute 107. From then on the door opening lies more than 45 minutes back, and `ExcursionAfterDoor` Ends although the temperature stays above 8 °C until minute 110.
- Narrow the window to 5 minutes: the first excursion is cut short in the same way, at minute 18.

## Interruptions: unless

```java
Rule lift = Rule.named("Lift").when(weight.above(500));
Rule maintenance = Rule.named("Maintenance").when(serviceMode.above(0.5));
Rule liftSinceService = Rule.named("LiftSinceService").when(after(lift).unless(maintenance));
```

`LiftSinceService` holds once a `Lift` has Ended, until a `Maintenance` Event is Confirmed. Interrupting Events are named explicitly with `unless` rather than implied, so a sequence means exactly what it says. [ADR-0002](https://github.com/romanhaendler/rbed/blob/main/docs/adr/0002-explicit-unless-for-sequences.md) explains why.

The [Pulse Channels](../pulse-channels/) page uses `after(...).within(...)` for errors after a deployment, and the [Building services](../building-services/) example for a compressor that restarts too soon.

## Reference

- [after: sequences](https://github.com/romanhaendler/rbed#after-sequences) in the README, the canonical text
- [After](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html) in the Javadoc

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Conditions.after`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28java.time.Duration%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29), [`After.unless`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#unless%28io.github.romanhaendler.rbed.Rule...%29).
