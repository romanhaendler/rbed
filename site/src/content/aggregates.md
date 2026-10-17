# Aggregates

Every Event carries Aggregates: for each Channel its Rule aggregates, the minimum, maximum and mean over the Samples of the Event, and more. They tell you what the data looked like while the Event ran, without keeping the Samples yourself.

## Which Channels

An Event carries the Aggregates of the Channels its Rule's Conditions use, plus those named with `aggregate(...)` (see [Aggregates for more Channels](https://github.com/romanhaendler/rbed#aggregates-for-more-channels-aggregate)). Read them with `event.aggregates(x)`.

Reading a Channel the Rule does not aggregate throws an `IllegalArgumentException` that names the missing `.aggregate(...)`, so a forgotten declaration is never mistaken for "no data".

## A vehicle's drives

`Drive` is an Event while `speed` is above a threshold. Its Condition uses `speed`, so `speed` is aggregated anyway; `.aggregate(temp)` adds the engine temperature. The log is irregular: a Sample every 2 seconds while the speed changes, every 10 seconds otherwise.

<!-- live -->
Select a Drive in the chart or in the table to see all its Aggregates.

<!-- scenario aggregates draw -->

At the default threshold of 1 m/s there are two Drives, from 22 s to 70 s and from 102 s to 132 s.

### Things to try

- Raise *Driving above* to 10 m/s: the second drive, never faster than 8 m/s, is no Drive any more, and the first one is shorter, from 28 s to 64 s. Its Aggregates change with its span: its mean speed rises.

The Event table on every page of this site shows each Event's minimum, maximum and mean per Channel under the Event.

## The components

- `min`, `max`, `mean`: over the Samples of the Event in which the Channel had a value.
- `count`: the number of those Samples. If it is 0, `min`, `max` and `mean` are NaN: the site's Event tables then say "no value", and the full Aggregates show a dash.
- `timeWeightedMean`: the mean with each value weighted by how long it held, until the next Sample, time advance or the Event's end.
- `integral`: value × seconds, time-weighted like the mean: distance from speed, energy from power, dose from rate.

The plain `mean` treats every Sample alike, which is right for regularly sampled data. With irregular sampling, as in the log above, `timeWeightedMean` gives the true average over time. At the default threshold, the first Drive's Samples average 9.33 m/s, but the vehicle spent most of the Drive at 14 m/s: its time-weighted mean is 11.67 m/s, and its integral of 560 is the distance it covered, in metres.

The time-weighted values are NaN if the Channel had no value during the Event, and the time-weighted mean also for an Event of zero length.

## While an Event runs

The listener receives the Event as it stands at each stage of its lifecycle. Aggregates of the Pending and the Confirmed Event cover the Event so far; those of the Ended Event cover it from its start to its end.

Another Rule can use them in its Conditions: `latest(rule).min(x)`, `.max(x)` and `.mean(x)` are the Aggregates of the most recent Confirmed Event of `rule` (see [The latest Event](../latest/)).

## Reference

- [Aggregates](https://github.com/romanhaendler/rbed#aggregates) and [Aggregates for more Channels](https://github.com/romanhaendler/rbed#aggregates-for-more-channels-aggregate) in the README, the canonical text
- [Aggregates](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Aggregates.html) and [Event](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html) in the Javadoc

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.aggregate`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#aggregate%28io.github.romanhaendler.rbed.Channel...%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.listener`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`EventListener.onEnded`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onEnded%28io.github.romanhaendler.rbed.Event%29), [`Event.aggregates`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#aggregates%28io.github.romanhaendler.rbed.Channel%29), [`Aggregates.timeWeightedMean`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Aggregates.html#timeWeightedMean%28%29), [`Aggregates.max`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Aggregates.html#max%28%29), [`Aggregates.integral`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Aggregates.html#integral%28%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
