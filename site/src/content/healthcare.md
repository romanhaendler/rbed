# Healthcare

A bedside monitor reports heart rate and oxygen saturation (SpO2) at 1 Hz, and here its sensor drops out for 20 s. While no data arrives, the monitor's clock advances the Detector every second with `advanceTo`, so the dropout is noticed while it lasts, not only once data resumes.

<!-- scenario healthcare -->

## The Rules

- **Tachycardia**: heart rate above 100 for 30 s, over below 95 for 30 s. A dropout longer than 10 s interrupts it: the Event Ends Truncated, and the one after the dropout starts Truncated because nobody knows what happened in between.
- **Desaturation**: SpO2 below 90 % for 10 s, over at 92 % or more.
- **HypoxicTachycardia**: both at the same time.
- **SensorLost**: no heart rate for more than 5 s, measured with `age()`.

### Things to try

- Raise *Max gap* to 30 s: the 20 s dropout no longer interrupts `Tachycardia`, which runs as one Event from 60 s to 300 s, and `HypoxicTachycardia` lasts until `Desaturation` Ends at 200 s.

## Reference

The example is [VitalSignsExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/VitalSignsExample.java), a test with its expected Events, listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples).

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Rule.endWhen`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`Expression.below`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`Rule.maxGap`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#maxGap%28java.time.Duration%29), [`Condition.not`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#not%28%29), [`Conditions.during`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#during%28io.github.romanhaendler.rbed.Rule%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`ChannelExpression.age`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#age%28%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.advanceTo`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#advanceTo%28long%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).

The concepts at work, each on a page of its own: [Silent sensors: advancing time](../silent-sensors/), [Data gaps](../data-gaps/), [During another Event](../during/), [End Conditions and hysteresis](../end-conditions/).
