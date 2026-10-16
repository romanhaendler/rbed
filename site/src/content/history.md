# Restarts: History

A Detector keeps its state in memory. After a restart, a Rule like `after(maintenance)` has forgotten the last maintenance. Pass the Events stored before the restart to the new Detector as History, and it carries on as if it had never stopped.

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

A machine reports whether it is in service mode once a day, for 200 days; it is serviced on days 10 and 140. `OverdueService` holds while there was no `Maintenance` in the last 90 days. The vertical line marks the restart: the Detector is closed and a new one takes over from that day.

<!-- scenario history draw -->

With History, the restarted Detector knows the maintenance of day 10, so the service is overdue from day 102, when that maintenance lies more than 90 days back, until the next one is over on day 141. Choose "Without History", and the new Detector knows of no maintenance at all: from the restart on day 60 the service counts as overdue, at once and with a Truncated start.

The Detector before the restart is in the same position at day 0: it has no History, so the service counts as overdue until the first maintenance it sees. Move the restart to day 120, more than 90 days after that maintenance, and History makes no difference: the service is overdue either way.

## What History is used for

History Events are matched to Rules by `name()`, so each must belong to a Rule added to the Detector. They must be Confirmed and Ended (both `confirmed()` and `end()` present), and the first Sample must come after the latest of their ends. `after`, `unless`, `count`, `totalDuration` and `latest(...).duration()` take them into account. They carry no Aggregates for `latest(...).min/max/mean`, and they are not reported to the listener again.

## Reference

- [Restarts: History](https://github.com/romanhaendler/rbed#restarts-history) in the README, the canonical text
- [Detector.Builder](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html) in the Javadoc: `history`

Methods used: [`when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`after`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`within`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28java.time.Duration%29), [`not`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#not%28%29), [`history`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#history%28java.util.Collection%29), [`listener`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#listener%28io.github.romanhaendler.rbed.EventListener%29), [`close`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).
