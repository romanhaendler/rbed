# 03: Watch Expressions about Rules, and every edge case

**What to build:** Every kind of Expression can be watched and read, and every way of feeding a Detector reports correct values. Expressions about other Rules' Events (`count(rule).within(...)`, `totalDuration(rule)`, `latest(rule)...`) are bound after all Rules, so they may refer to any added Rule; a reference to a Rule not added is rejected by `build()` as for Rules. Messages produced while binding a watched Expression (for example `averageOverSamples(n)` above the Retention Limit) name the Expression instead of a Rule. Pulse Channels, time advances, sampling-rate mode, batch processing, reads from EventListener callbacks and reads after `close()` all behave as the spec states. See spec: User Stories 10-19; Implementation Decisions "New public API", "Binding".

**Blocked by:** 02

**Status:** done

- [x] `count(rule).within(...)`, `totalDuration(rule)` and an Expression from `latest(rule)` can be watched and read; their values follow the Rule's Events
- [x] A watched Expression referring to a Rule not added to the Detector is rejected by `build()` with a message naming the Rule
- [x] A watched `averageOverSamples(n)` above the Retention Limit is rejected by `build()` with a message naming the watched Expression, not a Rule
- [x] A Pulse Channel reads its value in a Sample that carries it and empty in a moment that does not (never a held value)
- [x] After `advanceTo`, values reflect that moment (`age()` has grown)
- [x] Reads work in sampling-rate mode and during `detect(...)` / `detectValues(...)`
- [x] Reads from inside `onPending`, `onConfirmed` and `onEnded` report the moment being processed
- [x] After `close()`, `value(...)` reports the last moment processed
- [x] JUnit tests through the public API cover every criterion above (prior art: TimeAdvanceTest, PulseChannelTest, RetentionTest, EventCountAndDurationTest, LatestEventTest)
- [x] `mvn -q verify` passes

## Comments

- Expressions now have a readable `toString` mirroring how they are written in Java: `x.averageOverSamples(5)`, `x.minus(y).times(2.5)`, `count(Run).within(60000)`, `totalDuration(Run)`, `latest(Run).max(x)`. It is a `text` field passed to the package-private `Expression(String)` constructor (built once when the Expression is created, never per Sample); `Channel` no longer overrides `toString`. Constants print as Java doubles (`plus(1.0)`), durations in milliseconds (a `Duration` argument shows as its millis). Conditions are untouched (still `Object`'s default). Pinned in `ExpressionValueTest.anExpressionReadsAsItIsWrittenInJava`.
- No library behaviour change was needed for the edge cases: the binding added in ticket 02 already handled Rule references, Pulse Channels, advances, sampling-rate mode, batch processing, callbacks and `close()`. All of it is now pinned in `ExpressionValueTest`.
- Binding messages for a watched Expression read e.g. `Watched Expression totalDuration(Other) references Other, which is not added` and `Watched Expression x.averageOverSamples(5): averageOverSamples(5) exceeds the Retention Limit of 3. ...`. The README's "Errors and validation" table lists these and the unwatched read; the `build()` and `retentionLimit` Javadoc mention watched Expressions.
- A read from a callback reports the moment being processed. Event-based Expressions (`count`, `totalDuration`, `latest`) of a Rule evaluated later in declaration order still show the previous moment's Event state at that point, just as a Rule sees them.
- Watching `latest(R).mean(c)` / `min` / `max` makes R aggregate `c` (as a Rule using it already does), so R's Events then also carry Aggregates of `c`. Which Events are found, and when, does not change. The tests use a Channel R already aggregates.
- The site builds and its tests (`npm test`, `test:browser`) pass unchanged.

