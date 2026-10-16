# Restarts: History

A Detector keeps its state in memory. After a restart, a Rule like `after(maintenance)` has forgotten the last maintenance. Pass the Events stored before the restart to the new Detector as History, and Rules that refer to earlier Events (`after`, `unless`, `count`, `totalDuration`, `latest(...).duration()`) carry on across the restart. History restores no Channel values, moving averages or running Events: an Event running at the restart is Ended Truncated by `close()`, and the new Detector starts a new one with a Truncated start.

## Storing Events and handing them back

Before the restart, store the Ended Events, for example in `onEnded`. After it, load them and give them to the builder with `history(...)`:

```java
// before the restart: store Ended Events, e.g. in onEnded
List<Event> stored = loadFromDatabase();

Rule maintenance = Rule.named("Maintenance").when(serviceMode.above(0.5));
Rule overdueService = Rule.named("OverdueService")
    .when(after(maintenance).within(Duration.ofDays(90)).not());
Detector detector = Detector.builder()
    .rules(maintenance, overdueService)
    .history(stored)
    .build();
```

## A service that is due every 90 days

A machine reports whether it is in service mode once a day, for 200 days; it is serviced on days 10 and 140. `OverdueService` holds while there was no `Maintenance` in the last 90 days. At the restart, the Detector is closed and a new one takes over from that day.

<!-- live -->
The vertical line marks the restart.

<!-- scenario history draw -->

At the defaults, with History, the restarted Detector knows the maintenance of day 10, so the service is overdue from day 102, when that maintenance lies more than 90 days back, until the next one is over on day 141. Choose "Without History", and the new Detector knows of no maintenance at all: from the restart on day 60 the service counts as overdue, at once and with a Truncated start.

The Detector before the restart is in the same position at day 0: it has no History, so the service counts as overdue until the first maintenance it sees.

### Things to try

- Move the restart to day 120, more than 90 days after the maintenance of day 10: History makes no difference. The service is overdue either way, split at the restart into two Truncated Events: one Ended by `close()` at day 119, and one that starts with the new Detector at day 120.

## What History is used for

History Events are matched to Rules by `name()`, so each must belong to a Rule added to the Detector. They must be Confirmed and Ended (both `confirmed()` and `end()` present), and the first Sample must come after the latest of their ends. `after`, `unless`, `count`, `totalDuration` and `latest(...).duration()` take them into account. They carry no Aggregates for `latest(...).min/max/mean`, and they are not reported to the listener again.

## Reference

- [Restarts: History](https://github.com/romanhaendler/rbed#restarts-history) in the README, the canonical text
- [Detector.Builder](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html) in the Javadoc: `history`

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Conditions.after`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28java.time.Duration%29), [`Condition.not`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#not%28%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.listener`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`EventListener.onEnded`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventListener.html#onEnded%28io.github.romanhaendler.rbed.Event%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29), [`Detector.Builder.history`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#history%28java.util.Collection%29).
