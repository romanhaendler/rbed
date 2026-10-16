# Environment

A river gauge reports the water level every 15 minutes; the report at 20 h is missing. Here the level rises 20 cm/h to a crest after 12 h and then falls 10 cm/h. The rate is taken of the average of the last 4 reports, so a noisy gauge would not make the warning flicker.

<!-- scenario environment -->

## The Rules

- **FloodWarning**: above 300 cm and still rising faster than 10 cm/h, for 30 minutes; lifted only once below 250 cm for 2 hours. Reports more than 2 hours apart interrupt it. It starts at 5.25 h (305 cm) and ends at 31.25 h (247.5 cm).
- **Receding**: falling while still above 300 cm, from 12.25 h (437.5 cm) to 26 h, where the level is exactly 300 cm.

Lower the gap below the 30 minutes between the reports around 20 h and the missing report interrupts the warning.

## Source and reference

The example is [FloodWarningExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/FloodWarningExample.java), listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples) with its expected Events.

Methods used: [`Rule.when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`forDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`endWhen`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`maxGap`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#maxGap%28long%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`below`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`averageOverSamples`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverSamples%28int%29), [`rate`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#rate%28%29), [`falling`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#falling%28%29), [`and`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29).
