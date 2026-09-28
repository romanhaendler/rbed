# 02: Channels as typed handles

**What to build:** Users create Channels once with `Channel.named("speed")` or `Channel.pulse("deployment")` and use them everywhere a Channel name was used: in Conditions (`speed.above(1)`, `speed.rate()`, moving averages), in `aggregate(...)`, when pushing values (`Map<Channel, Double>`, also in `Sample` and `detectValues`), in `event.aggregates(speed)` and in `latest(...).min/max/mean(speed)`. Channels are equal by name. The pulse declaration on the Detector builder and the `channel(String)` factory are gone. Rule references stay strings in this ticket. See spec, user stories 1–5, 15–20, and ADR-0003.

**Blocked by:** 01

**Status:** done

- [x] `Channel.named` / `Channel.pulse` exist; a Channel is usable wherever a Channel Expression was
- [x] `build()` throws `IllegalArgumentException` when one name is referenced both as a Pulse Channel and as an ordinary Channel
- [x] Pushing with `Channel.named(x)` created separately from the Rule's Channel reaches the Rule (equality by name); values of Channels no Rule uses are ignored
- [x] `event.aggregates(channel)` still throws for a Channel the Rule does not aggregate
- [x] No string-based Channel API remains in the public API
- [x] All tests, examples and the README migrated; expected Events unchanged
- [x] The performance test's zero-allocation check still passes
