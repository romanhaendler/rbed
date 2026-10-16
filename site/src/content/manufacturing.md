# Manufacturing

A machine tool's spindle temperature and load are sampled at 1 Hz without timestamps, so the Detector is built with a sampling rate and derives each Sample's timestamp from its position (sampling-rate mode). Here the spindle heats up at 1 °C/s to 100 °C under load, stays there, and cools down again.

<!-- scenario manufacturing -->

## The Rules

- **Overheat**: above 80 °C for 30 s, over only once below 70 °C for a minute (hysteresis).
- **HighLoad**: load above 80 % for 10 s, over below 60 %.
- **OverheatUnderLoad**: both at once, `during` combined with `and`.
- **FastHeating**: heating faster than 0.5 °C/s for 5 consecutive Samples.

`during(overheat)` stays true until Overheat's End Condition has held for its minute (391 s), although its end timestamp is where the End Condition began (331 s). A streaming Detector cannot know earlier that the Event is over.

### Things to try

- Raise *Overheat for* to 60 s: `Overheat` is Confirmed at 181 s instead of 151 s, and `OverheatUnderLoad` with it.

## Reference

The example is [MachineToolOverheatExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/MachineToolOverheatExample.java), a test with its expected Events, listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples).

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Rule.endWhen`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`Expression.below`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`Conditions.during`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#during%28io.github.romanhaendler.rbed.Rule%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`ChannelExpression.rate`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#rate%28%29), [`Rule.forSamples`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forSamples%28int%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.samplingRate`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#samplingRate%28double%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.detectValues`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#detectValues%28java.lang.Iterable%29).

The concepts at work, each on a page of its own: [Batch processing and sampling rates](../sampling-rate/), [Conditions and Expressions](../conditions-and-expressions/), [During another Event](../during/), [End Conditions and hysteresis](../end-conditions/).
