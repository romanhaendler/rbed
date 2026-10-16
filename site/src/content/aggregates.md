# Aggregates

Every Event carries Aggregates: for each Channel its Rule aggregates, the minimum, maximum and mean over the Samples of the Event, and more. They tell you what the data looked like while the Event ran, without keeping the Samples yourself.

## Which Channels

An Event carries the Aggregates of the Channels its Rule's Conditions use, plus those named with `aggregate(...)` (see [Aggregates for more Channels](https://github.com/romanhaendler/rbed#aggregates-for-more-channels-aggregate)). Read them with `event.aggregates(x)`.

Reading a Channel the Rule does not aggregate throws an `IllegalArgumentException` that names the missing `.aggregate(...)`, so a forgotten declaration is never mistaken for "no data".

## A vehicle's drives

`Drive` is an Event while `speed` is above a threshold. Its Condition uses `speed`, so `speed` is aggregated anyway; `.aggregate(temp)` adds the engine temperature. The log is irregular: a Sample every 2 seconds while the speed changes, every 10 seconds otherwise.

Select a Drive in the chart or in the table to see all its Aggregates. Move the threshold, and the Aggregates change with the Event's span.

<!-- scenario aggregates draw -->

The Event table on every page of this site shows each Event's minimum, maximum and mean per Channel under the Event.

## The components

- `min`, `max`, `mean`: over the Samples of the Event in which the Channel had a value.
- `count`: the number of those Samples. If it is 0, `min`, `max` and `mean` are NaN, which the site shows as a dash.
- `timeWeightedMean`: the mean with each value weighted by how long it held, until the next Sample, time advance or the Event's end.
- `integral`: value × seconds, time-weighted like the mean: distance from speed, energy from power, dose from rate.

The plain `mean` treats every Sample alike, which is right for regularly sampled data. With irregular sampling, as in the log above, `timeWeightedMean` gives the true average over time. At the default threshold, the first Drive's Samples average 9.33 m/s, but the vehicle spent most of the Drive at 14 m/s: its time-weighted mean is 11.67 m/s, and its integral of 560 is the distance it covered, in metres.

The time-weighted values are NaN if the Channel had no value during the Event, and the time-weighted mean also for an Event of zero length.

## While an Event runs

The listener receives the Event as it stands at each stage of its lifecycle. Aggregates of the Pending and the Confirmed Event cover the Event so far; those of the Ended Event cover it from its start to its end.

Another Rule can use them in its Conditions: `latest(rule).min(x)`, `.max(x)` and `.mean(x)` are the Aggregates of the most recent Confirmed Event of `rule` (see [latest](https://github.com/romanhaendler/rbed#latest-values-of-the-most-recent-event)).

The README's [Aggregates](https://github.com/romanhaendler/rbed#aggregates) chapter is the reference for this page.

Methods used: [`when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`aggregate`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#aggregate%28io.github.romanhaendler.rbed.Channel...%29), [`listener`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`aggregates`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#aggregates%28io.github.romanhaendler.rbed.Channel%29), [`timeWeightedMean`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Aggregates.html#timeWeightedMean%28%29), [`max`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Aggregates.html#max%28%29), [`integral`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Aggregates.html#integral%28%29).
