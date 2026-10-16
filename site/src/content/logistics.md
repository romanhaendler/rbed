# Logistics

A refrigerated truck carries vaccines, which must stay between 2 and 8 °C; its temperature and door sensors report every minute. Regulations limit the total time above 8 °C over the trip to 45 minutes, across all excursions, and an excursion soon after the door was open is probably caused by it.

<!-- scenario logistics -->

## The Rules

- **Excursion**: above 8 °C for 15 minutes, over as soon as it is back at 8 °C or below.
- **DoorOpen**: the door sensor reads 1, here from minute 60 to 65.
- **ExcursionAfterDoor**: above 8 °C within 30 minutes after the door closed, to attribute the cause. It starts at 65, the moment the door closed; the second Excursion, from 300, is too long after it.
- **CumulativeLimit**: the total time of the Excursions above 45 minutes. The first adds 28 minutes; the second, Confirmed at 315, brings the total to 46 at 318. It holds until the data ends, so its end is Truncated.

## Source and reference

The example is [ColdChainExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/ColdChainExample.java), listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples) with its expected Events.

Methods used: [`Rule.when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`forDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`endWhen`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`not`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#not%28%29), [`and`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`after`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28long%29), [`totalDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#totalDuration%28io.github.romanhaendler.rbed.Rule%29).
