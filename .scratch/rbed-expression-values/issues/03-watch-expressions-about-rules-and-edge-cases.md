# 03: Watch Expressions about Rules, and every edge case

**What to build:** Every kind of Expression can be watched and read, and every way of feeding a Detector reports correct values. Expressions about other Rules' Events (`count(rule).within(...)`, `totalDuration(rule)`, `latest(rule)...`) are bound after all Rules, so they may refer to any added Rule; a reference to a Rule not added is rejected by `build()` as for Rules. Messages produced while binding a watched Expression (for example `averageOverSamples(n)` above the Retention Limit) name the Expression instead of a Rule. Pulse Channels, time advances, sampling-rate mode, batch processing, reads from EventListener callbacks and reads after `close()` all behave as the spec states. See spec: User Stories 10-19; Implementation Decisions "New public API", "Binding".

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] `count(rule).within(...)`, `totalDuration(rule)` and an Expression from `latest(rule)` can be watched and read; their values follow the Rule's Events
- [ ] A watched Expression referring to a Rule not added to the Detector is rejected by `build()` with a message naming the Rule
- [ ] A watched `averageOverSamples(n)` above the Retention Limit is rejected by `build()` with a message naming the watched Expression, not a Rule
- [ ] A Pulse Channel reads its value in a Sample that carries it and empty in a moment that does not (never a held value)
- [ ] After `advanceTo`, values reflect that moment (`age()` has grown)
- [ ] Reads work in sampling-rate mode and during `detect(...)` / `detectValues(...)`
- [ ] Reads from inside `onPending`, `onConfirmed` and `onEnded` report the moment being processed
- [ ] After `close()`, `value(...)` reports the last moment processed
- [ ] JUnit tests through the public API cover every criterion above (prior art: TimeAdvanceTest, PulseChannelTest, RetentionTest, EventCountAndDurationTest, LatestEventTest)
- [ ] `mvn -q verify` passes
