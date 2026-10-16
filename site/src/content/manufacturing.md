# Manufacturing

A machine tool's spindle temperature and load are sampled at 1 Hz without timestamps, so the Detector is built with a sampling rate and derives each Sample's timestamp from its position (sampling-rate mode). Here the spindle heats up at 1 °C/s to 100 °C under load, stays there, and cools down again.

<!-- scenario manufacturing -->

## The Rules

- **Overheat**: above 80 °C for 30 s, over only once below 70 °C for a minute (hysteresis).
- **HighLoad**: load above 80 % for 10 s, over below 60 %.
- **OverheatUnderLoad**: both at once, `during` combined with `and`.
- **FastHeating**: heating faster than 0.5 °C/s for 5 consecutive Samples.

`during(overheat)` stays true until Overheat's End Condition is confirmed (391 s), although its end timestamp is where the End Condition began (331 s). A streaming Detector cannot know earlier that the Event is over.

## Source and reference

The example is [MachineToolOverheatExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/MachineToolOverheatExample.java), listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples) with its expected Events.

Methods used: [`samplingRate`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#samplingRate%28double%29), [`detectValues`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#detectValues%28java.lang.Iterable%29), [`Rule.when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`forDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`endWhen`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`forSamples`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forSamples%28int%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`below`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`rate`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#rate%28%29), [`during`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#during%28io.github.romanhaendler.rbed.Rule%29), [`and`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29).

The concepts at work, each on a page of its own: [Batch processing and sampling rates](../sampling-rate/), [Conditions and Expressions](../conditions-and-expressions/), [During another Event](../during/), [End Conditions and hysteresis](../end-conditions/).
