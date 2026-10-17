# The Event lifecycle

An Event does not appear all at once. It goes **Pending** when its Rule's Conditions start holding, becomes **Confirmed** once they have held for the required duration, and **Ends** later. A Pending Event whose Conditions stop holding too early is **Discarded** and never counts.

## Four callbacks

The Detector tells its `EventListener` about each stage. All four methods are optional, so implement only the ones you need:

- `onPending`: the Conditions started holding; the required duration is not reached yet.
- `onConfirmed`: the Conditions held long enough. Only Confirmed Events count as detected.
- `onEnded`: a Confirmed Event is over. It now has its end, and its Aggregates cover the whole Event.
- `onDiscarded`: a Pending Event stopped holding before it was Confirmed.

## Pending, then Confirmed or Discarded

`Overheat` requires `temp` to stay above 80 for a duration, two seconds by default.

<!-- live -->
On the Rule's lane, the band is pale while the Event is Pending and solid once it is Confirmed. The dots mark the moments it went Pending, was Confirmed, Ended or was Discarded. Hover over the chart to read the state at each Sample, and change the duration to see what `forDuration` does. Set Draw to `temp` and draw a spike of your own: shorter than the duration, it is Discarded.

<!-- scenario event-lifecycle draw -->

At the default duration of two seconds:

- The first Event goes Pending at 3 s, is Confirmed at 5 s and Ends at 9 s, when `temp` falls to 76.
- The second goes Pending at 12 s and is Confirmed at 14 s, just in time: at 15 s `temp` is back at 75.
- The spike at 17 s lasts a single Sample. At 18 s the Conditions no longer hold, so the Pending Event is Discarded. It never counts as detected: it is not in the Event table, only among the Discarded Pending Events.

### Things to try

- Raise the duration to 3 s: the second Event is Discarded as well. Its three seconds would be reached at 15 s, but by then `temp` has dropped.
- Raise it to 6 s: not even the first Event is Confirmed.
- Set it to 0 s: every Event is Confirmed the moment it starts, and the spike counts, too.

## The start lies before the confirmation

An Event's `start()` is where its Conditions started holding, not where it was Confirmed: the first Event starts at 3 s, although it is only known at 5 s. `confirmed()` returns the moment of confirmation, which the Event table shows in a column of its own.

A Detector only sees what its Samples show. The Discarded moment on this page is the Sample at which the Conditions were found no longer holding. The `Event` passed to `onDiscarded` has no such timestamp, only its start.

## Durations: forDuration and forSamples

`forDuration` measures from the Event's start, so the required duration is reached at `start()` plus the duration, and the Event is Confirmed at the first Sample or [advance](../silent-sensors/) from then on. `forSamples(n)` instead requires at least `n` consecutive Samples; with both, both must be met. Each applies to what comes right before it: `when(...)`, or `endWhen(...)` for an [End Condition](../end-conditions/#a-duration-for-the-end-condition). Without either, an Event is Pending and Confirmed at the same moment.

## One Event at a time

Each Rule has at most one Event in progress. A new Event only starts once its Rule has seen `when` not holding after the previous Event Ended (or was Discarded), or at the first Sample after a [data gap](../data-gaps/) longer than `maxGap`, with a Truncated start. So a Rule with an End Condition does not flicker: see [End Conditions](../end-conditions/#when-does-the-next-event-start).

## What an Event carries

An `Event` is an immutable record, and the listener receives a fresh snapshot at each stage. It carries:

- `name()`: the name of the Rule that produced it, for example to store the Event.
- `is(rule)`: whether `rule` produced it.
- `start()`: the timestamp (ms) of the first moment at which `when` held.
- `confirmed()`: an `OptionalLong`, when it was Confirmed; empty while Pending or if Discarded.
- `end()`: an `OptionalLong`, when it Ended; empty until then.
- `truncatedStart()`: it may have begun earlier than `start()`.
- `truncatedEnd()`: observation stopped while it ran, by `close()` or a data gap.
- `aggregates(x)`: the [Aggregates](../aggregates/) of Channel `x` over the Event so far.

The duration of an Ended Event is `e.end().getAsLong() - e.start()`.

## Truncated Events

Two flags mark Events whose true extent is not known. The Event table shows both in its Truncated column.

- `truncatedEnd()`: observation stopped while the Event was running. `close()` Ends a Confirmed Event this way (and Discards a Pending one), and so does a data gap.
- `truncatedStart()`: the Event may have begun before its `start()`, because its Rule had not yet seen its Conditions *not* holding: the very first Sample already satisfies them, a moving average is still warming up, or a data gap has just ended.

The [first Rule](../#a-first-rule) on the front page shows a Truncated start: lower its threshold to 70, and its Event starts at the very first Sample.

## Reference

- [The Event lifecycle](https://github.com/romanhaendler/rbed#the-event-lifecycle), [Events](https://github.com/romanhaendler/rbed#events) and [Durations: forDuration and forSamples](https://github.com/romanhaendler/rbed#durations-forduration-and-forsamples) in the README, the canonical text
- [EventListener](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html) and [Event](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html) in the Javadoc

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.listener`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`EventListener.onPending`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onPending%28io.github.romanhaendler.rbed.Event%29), [`Event.start`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#start%28%29), [`EventListener.onConfirmed`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onConfirmed%28io.github.romanhaendler.rbed.Event%29), [`Event.confirmed`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#confirmed%28%29), [`EventListener.onEnded`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onEnded%28io.github.romanhaendler.rbed.Event%29), [`Event.end`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#end%28%29), [`EventListener.onDiscarded`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onDiscarded%28io.github.romanhaendler.rbed.Event%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
