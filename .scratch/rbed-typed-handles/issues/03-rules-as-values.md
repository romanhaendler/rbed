# 03: Rules as immutable values

**What to build:** Users define Rules as standalone immutable values with `Rule.named("Trip").when(...)...` and add them with `Detector.builder().rules(trip, idling)`. `during`, `after(...).unless(...)`, `count`, `latest` and `totalDuration` take Rules instead of names. `event.is(trip)` tells which Rule produced an Event; `event.name()` stays for storage and History. `build()` rejects every reference that could silently never hold. The old `rule(name, config)` and the internal runtime class's name clash are gone. See spec, user stories 6–14, 21–23, 25, and ADR-0003.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] Configuring a Rule returns a new Rule; the original is unchanged and can be used in several Detectors
- [ ] `build()` throws `IllegalArgumentException` for: a Rule without `when`, duplicate Rule names, a reference to a Rule not added, a reference to itself or to a Rule added later, a History Event whose name matches no added Rule
- [ ] `event.is(rule)` is true exactly for Events of that Rule
- [ ] No string-based Rule reference remains in the public API
- [ ] All tests, examples and the README migrated; expected Events unchanged
- [ ] The performance test's zero-allocation check still passes
