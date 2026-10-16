# Building services

An air conditioner's compressor power is measured every 10 s. Short cycling, starting again shortly after stopping, wears compressors out, so the Rules look for Runs that begin soon after the previous one and for many starts within an hour.

<!-- scenario building-services -->

## The Rules

- **Run**: above 500 W for 30 s, over below 100 W for 30 s. A plain method classifies a Run shorter than 5 minutes as a short cycle, shown as a note in the table.
- **QuickRestart**: a Run beginning within 3 minutes after the previous one Ended. It Ends at once below 100 W, while Run waits 30 s to confirm its end.
- **FrequentStarts**: more than 2 Runs Confirmed within an hour. The Runs are Confirmed at 130, 830 and 1630 s, and the window keeps all three until the data ends, so the Event's end is Truncated.

## Source and reference

The example is [HvacShortCyclingExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/HvacShortCyclingExample.java), listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples) with its expected Events.

Methods used: [`Rule.when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`forDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`endWhen`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`below`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`after`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28long%29), [`count`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#count%28io.github.romanhaendler.rbed.Rule%29), [`EventCount.within`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventCount.html#within%28long%29), [`and`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29).

The concepts at work, each on a page of its own: [After another Event](../after/), [Counting Events](../count/), [End Conditions and hysteresis](../end-conditions/).
