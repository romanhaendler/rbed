# 01: Aggregates only for the Channels a Rule uses

**What to build:** Each Rule keeps Aggregates only for the Channels referenced in its `when`/`endWhen` Conditions and those named with `.aggregate(...)`. Reading Aggregates of any other Channel from its Events throws. The per-Sample cost scales with the Rule's own Channels, not with all Channels. See spec (A).

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Referenced Channels are aggregated automatically; `.aggregate("x", ...)` adds more
- [x] `event.aggregates("other")` throws `IllegalArgumentException` naming the Rule and Channel
- [x] Existing tests and examples updated where they read non-referenced Channels (e.g. crane `weight` is referenced; Aggregates test for `height` needs `.aggregate`)
- [x] Performance: a scenario with many Channels but few referenced ones; the 50x20 floor is re-measured and raised
- [x] Zero steady-state allocation still holds
