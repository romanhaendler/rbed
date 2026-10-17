# Resuming a stream

An application that reads a message stream (a Kafka topic, say) wants to stop and start again, for a deployment or after a crash, and continue as if it had never stopped: the same Events at the same timestamps with the same flags and Aggregates, each reported once. [History](../history/) restores only Ended Events. Three capabilities restore the rest (Events in progress, running durations, moving-average windows, `rate()`, `age()`, `maxGap`, `latest(...)` Aggregates):

- **Snapshot.** [`detector.snapshot()`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#snapshot%28%29) returns the complete state as a `byte[]`, and [`restore(bytes)`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#restore%28byte%5B%5D%29) on the builder continues from it, with nothing to replay. A restored Detector is indistinguishable from the original in everything observable. Take it between moments (never inside a listener callback) and store it with the position of the next message.
- **Replay Point.** [`detector.replayPoint()`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#replayPoint%28%29) is the earliest timestamp from which replaying the stream into a fresh Detector reproduces the state, when the Events that Ended before it are supplied as History. Nothing is stored but a timestamp and Ended Events. It is the way to continue when there is no usable Snapshot, and it bounds the replay after an older one.
- **Memory Horizon.** [`detector.memoryHorizon()`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#memoryHorizon%28%29) is the longest time window and the largest Sample count the Rules use. It sizes the topic's retention and the Snapshot interval. It is a configuration bound, not a guarantee: an Event in progress can reach further back. Only the Replay Point is exact.

## A machine that overheats

A machine reports its temperature and load once a second, 120 messages. `Overheat` holds while the temperature is above 80 for 3 seconds, and is over when the load drops below 10, which can happen while the temperature is still high. `Repeated` holds while more than one `Overheat` was Confirmed within the last minute.

The process dies after a message, and a new process takes over from the next one. Choose the stop and how the new process restarts, and compare its Events with those of a Detector that never stopped.

<!-- live -->
The shaded stretch before the stop is the Memory Horizon, the line left of the stop is the Replay Point, and Events that differ from the uninterrupted run are shaded in red.

<!-- scenario resuming -->

At the defaults, the process dies after message 45, at 44 s, while the second `Overheat` is running (Pending since 40 s, Confirmed at 43 s). The uninterrupted Detector reports `Overheat` from 10 to 20 s and from 40 to 60 s, `Repeated` from 43 to 74 s, then `Overheat` from 90 to 100 s and `Repeated` from 93 to 104 s.

- **Snapshot** and **Replay Point** report exactly these Events. The Replay Point at the stop is 39 s: the replay has to start at the last Sample on which `Overheat` was seen not to hold, so that the Event starts at 40 s again and is not Truncated. The Memory Horizon is the minute of `count(...).within(...)`, 60 s and no Samples; the Replay Point is much closer.
- **History only** knows the Events that Ended before the stop, but not the one in progress. The new Detector starts `Overheat` at 45 s with a Truncated start, and `Repeated` therefore starts at 48 s, not 43 s.
- **Naive**, a fresh Detector, knows nothing: `Overheat` also starts at 45 s Truncated. It has lost the `Overheat` Confirmed at 13 s as well, so `Repeated` does not start at 48 s at all, and its second one, from 93 s, counts the `Overheat` of 45 s instead of 40 s and ends at 109 s, five seconds late.

### Things to try

- Move the stop to message 5, before anything happened: all four restarts equal the uninterrupted run, there is nothing to lose.
- Move it to message 80, after the second `Overheat`: History only is exact too, since every Event has Ended. The naive restart loses the count of Events, so `Repeated` is missing at 93 s.
- Move it to message 105, after the last `Overheat`: again nothing is left to lose.
- Move it to message 100, while the third `Overheat` is running: History only fails again, and the Replay Point is 89 s, just before that `Overheat` went Pending.

## What to persist, and how to restart

After each message (or each batch), in one transaction with its side effects, or idempotently, store the position of the next message; for the Snapshot recipe the Snapshot and the position after it (every message, or every few seconds or minutes); for the replay recipe the Replay Point and the Ended Events (from `onEnded`), keyed by Rule name and start, so that storing one twice changes nothing.

1. **Restore and continue.** Build the Detector with the same Rules and options and `.restore(snapshot)`, seek to the position after the Snapshot and go on.
2. **Replay from the Replay Point.** Build the Detector with `.history(...)` of the stored Ended Events that ended before the stored Replay Point, seek to the first message at or after the Replay Point and feed the messages from there. The replay reports Events that already happened, so suppress the listener's side effects until the replay reaches the last processed position.
3. **An older Snapshot plus the tail.** Restore as in 1, then replay the messages from the Snapshot's position to the last processed one with side effects suppressed, then continue. Snapshots can be taken rarely; the Replay Point is not needed.

A Snapshot written by one runtime restores in the other: the Snapshot of the Java code on this page is the one the page's compiled copy of rbed writes in your browser, and the site's tests restore each in the other and check that both continue identically.

Restoring needs a Detector built with the same Rules (also names and order), watched Expressions, sampling rate and Retention Limit, and no `history(...)`; anything else is rejected, so a deployment that changed a Rule falls back to the replay. The README has the full recipe with a runnable example, the preconditions and notes on Kafka.

## Reference

- [Resuming a stream](https://github.com/romanhaendler/rbed#resuming-a-stream) in the README, the canonical text
- [Detector](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html) in the Javadoc: `snapshot`, `replayPoint`, `memoryHorizon`
- [Detector.Builder](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html) in the Javadoc: `restore`, `history`

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Rule.endWhen`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#endWhen%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Expression.below`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#below%28double%29), [`Conditions.count`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#count%28io.github.romanhaendler.rbed.Rule%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.listener`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`Detector.Builder.history`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#history%28java.util.Collection%29), [`Detector.Builder.restore`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#restore%28byte%5B%5D%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`EventListener.onEnded`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onEnded%28io.github.romanhaendler.rbed.Event%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.replayPoint`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#replayPoint%28%29), [`Detector.snapshot`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#snapshot%28%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
