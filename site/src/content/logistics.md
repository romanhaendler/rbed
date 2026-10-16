# Logistics

A refrigerated truck carries vaccines, which must stay between 2 and 8 °C; its temperature and door sensors report every minute. Regulations limit the total time above 8 °C over the trip to 45 minutes, across all excursions, and an excursion soon after the door was open is probably caused by it.

<!-- scenario logistics -->

## The Rules

- **Excursion**: above 8 °C for 15 minutes, over as soon as it is back at 8 °C or below.
- **DoorOpen**: the door sensor reads 1, here from minute 60 to 65.
- **ExcursionAfterDoor**: above 8 °C within 30 minutes after the door closed, to attribute the cause. It starts at 65, the moment the door closed; the second Excursion, from 300, is too long after it.
- **CumulativeLimit**: the total time of the Excursions above 45 minutes. The first adds 28 minutes; the second, Confirmed at 315, brings the total to 46 at 318. It holds until the data ends, so its end is Truncated.

### Things to try

- Raise *Total time limit* to 60 minutes: the two Excursions add up to 58 minutes above 8 °C, and there is no `CumulativeLimit`.

## Reference

The example is [ColdChainExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/ColdChainExample.java), a test with its expected Events, listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples).

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Rule.endWhen`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`Condition.not`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#not%28%29), [`Conditions.after`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28java.time.Duration%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Conditions.totalDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#totalDuration%28io.github.romanhaendler.rbed.Rule%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).

The concepts at work, each on a page of its own: [After another Event](../after/), [Time spent in Events](../total-duration/), [End Conditions and hysteresis](../end-conditions/).
