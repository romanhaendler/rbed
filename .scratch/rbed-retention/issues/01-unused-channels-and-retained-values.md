# 01: Ignore unused Channels and report retained values

**What to build:** Values of Channels that no Rule uses leave no trace in the Detector, so a stream with ever-new Channel names no longer grows it. A new public `Detector.retainedValues()` reports how many entries the Detector currently holds: one per Channel state, one per value in each moving-average window, one per timestamp held for `count(...).within(...)`. It is a plain counter read, maintained without allocating on the hot path. The README gains a "Memory" subsection with the per-feature retention table and `retainedValues()`. See spec: Implementation Decisions "Unused Channels" and "Observability".

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Pushing many Samples, each with a fresh unused Channel name, leaves `retainedValues()` unchanged
- [ ] An invalid value of an unused Channel is still rejected, and the rejected Sample leaves no trace
- [ ] Channels used only through `.aggregate(...)` or `latest(...)` still work as before
- [ ] `retainedValues()` grows with a filling `averageOverDuration` window and shrinks as values leave it by time; it counts `averageOverSamples(n)` and `count(...).within(...)` timestamps
- [ ] Existing tests pass unchanged; the steady-state allocation checks still allocate nothing per Sample
- [ ] README "Memory" subsection lists what each feature retains and documents `retainedValues()`
