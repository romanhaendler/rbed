# The Event lifecycle

An Event does not appear all at once. It goes **Pending** when its Rule's Conditions start holding, becomes **Confirmed** once they have held for the required duration, and **Ends** later. A Pending Event whose Conditions stop holding too early is **Discarded** and never counts.

## Four callbacks

The Detector tells its `EventListener` about each stage. All four methods are optional, so implement only the ones you need:

- `onPending`: the Conditions started holding; the required duration is not reached yet.
- `onConfirmed`: the Conditions held long enough. Only Confirmed Events count as detected.
- `onEnded`: a Confirmed Event is over. It now has its end and its Aggregates.
- `onDiscarded`: a Pending Event stopped holding before it was Confirmed.

## Pending, then Confirmed or Discarded

`Overheat` requires `temp` to stay above 80 for a duration, two seconds by default. On the Rule's lane, the band is pale while the Event is Pending and solid once it is Confirmed. The dots mark the moments it went Pending, was Confirmed, Ended or was Discarded. Hover over the chart to read the state at each Sample, and change the duration to see what `forDuration` does. Set Draw to `temp` and draw a spike of your own: shorter than the duration, it is Discarded.

<!-- scenario event-lifecycle draw -->

At the default duration of two seconds:

- The first Event goes Pending at 3 s, is Confirmed at 5 s and Ends at 9 s, when `temp` falls to 76.
- The second goes Pending at 12 s and is Confirmed at 14 s, just in time: at 15 s `temp` is back at 75.
- The spike at 17 s lasts a single Sample. At 18 s the Conditions no longer hold, so the Pending Event is Discarded. It never counts as detected: it is not in the Event table, only among the Discarded Pending Events.

Raise the duration to 3 s and the second Event is Discarded as well: its three seconds would be reached at 15 s, but by then `temp` has dropped. At 6 s not even the first Event is Confirmed. At 0 s every Event is Confirmed the moment it starts, and the spike counts, too.

## The start lies before the confirmation

An Event's `start()` is where its Conditions started holding, not where it was Confirmed: the first Event starts at 3 s, although it is only known at 5 s. `confirmed()` returns the moment of confirmation, which the Event table shows in a column of its own. `forDuration` measures from the start, so the required duration is reached at `start()` plus the duration, and the Event is Confirmed at the first Sample from then on. Without a duration, an Event is Pending and Confirmed at the same moment.

A Detector only sees what its Samples show. The Discarded moment on this page is the Sample at which the Conditions were found no longer holding. The `Event` passed to `onDiscarded` has no such timestamp, only its start.

## One Event at a time

Each Rule has at most one Event in progress. A new Event of a Rule only starts after its `when` Condition has stopped holding since the previous Event started. With an End Condition that overlaps `when`, a Rule therefore does not flicker.

## Truncated Events

Two flags mark Events whose true extent is not known. The Event table shows both in its Truncated column.

- `truncatedEnd()`: observation stopped while the Event was running. `close()` Ends a Confirmed Event this way (and Discards a Pending one), and so does a data gap.
- `truncatedStart()`: the Event may have begun before its `start()`, because its Rule had not yet seen its Conditions *not* holding: the very first Sample already satisfies them, a moving average is still warming up, or a data gap has just ended.

The first Rule on the front page shows a Truncated start: lower its threshold to 70, and its Event starts at the very first Sample.

## Reference

- [The Event lifecycle](https://github.com/romanhaendler/rbed#the-event-lifecycle) and [Durations: forDuration and forSamples](https://github.com/romanhaendler/rbed#durations-forduration-and-forsamples) in the README, the canonical text
- [EventListener](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html) and [Event](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html) in the Javadoc

Methods used: [`when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`forDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`listener`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`start`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#start%28%29), [`confirmed`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#confirmed%28%29), [`end`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Event.html#end%28%29), [`push`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`close`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
