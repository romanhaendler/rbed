# Spec: typed handles for Channels and Rules

Status: done

## Problem Statement

Rules refer to Channels and to other Rules by string names. A misspelled name is silently accepted: a Condition on `channel("sped")` stays undefined forever, and `during("Trpi")` never becomes true, so the Rule simply never fires and nothing reports why. Only a reference to a Rule declared *after* the referencing one is rejected. The same name has to be repeated in every Condition, in `aggregate(...)`, `pulse(...)`, `push(...)` and `event.aggregates(...)`, and attributes come back as `Object` and must be cast.

## Solution

Channels and Rules become typed handles (see ADR-0003). A name is written once, where the handle is created, and every use is checked by the compiler:

- `Channel.named("speed")` and `Channel.pulse("deployment")` create Channels; Channels are equal by name. A Channel is an Expression, so `speed.above(1)` works directly.
- A Rule is an immutable value built with `Rule.named("Trip").when(...).forDuration(...)...` and handed to the Detector with `Detector.builder().rules(trip, idling)`.
- Everything that referred to a Rule by name (`during`, `after`, `unless`, `count`, `latest`, `totalDuration`) takes a Rule. Since a Rule variable cannot be used before it is defined, "only earlier Rules may be referenced" follows from Java scoping.
- Samples carry `Map<Channel, Double>`; Aggregates are read with `event.aggregates(speed)` and `latest(trip).max(speed)`.
- `event.is(trip)` tells which Rule produced an Event; `event.name()` stays as the stored value (e.g. for History).
- Rule attributes are removed: a plain method of the Ended Event replaces them with full type safety.

Before:

```java
Detector.builder()
    .rule("Trip", r -> r.when(channel("speed").above(1)).forDuration(10_000))
    .rule("Idling", r -> r.when(channel("rpm").above(600).and(during("Trip").not())))
    .build();
```

After:

```java
Channel speed = Channel.named("speed"), rpm = Channel.named("rpm");
Rule trip = Rule.named("Trip").when(speed.above(1)).forDuration(10_000);
Rule idling = Rule.named("Idling").when(rpm.above(600).and(during(trip).not()));
Detector.builder().rules(trip, idling).build();
```

## User Stories

1. As a library user, I want to create a Channel once with `Channel.named("speed")`, so that its name is written in exactly one place.
2. As a library user, I want to write Conditions directly on a Channel (`speed.above(1)`, `speed.rate()`, `speed.averageOverSamples(5)`), so that I do not need a separate `channel(...)` lookup.
3. As a library user, I want two Channels with the same name to be equal, so that I can map external data (e.g. CSV columns) onto Channels with `Channel.named(column)` without a registry.
4. As a library user, I want to declare a Pulse Channel with `Channel.pulse("deployment")`, so that its pulse nature is part of the Channel and cannot be forgotten on the Detector builder.
5. As a library user, I want `build()` to fail when the same name is used as a Pulse Channel and as an ordinary Channel, so that contradictory declarations are not silently resolved.
6. As a library user, I want to define a Rule as a standalone immutable value with `Rule.named("Trip")`, so that I can reference it from other Rules and reuse it.
7. As a library user, I want to configure a Rule fluently with `when`, `endWhen`, `forSamples`, `forDuration`, `maxGap` and `aggregate`, with the same meaning as before, so that existing knowledge carries over.
8. As a library user, I want each configuration call to return a new Rule and leave the original unchanged, so that a Rule can safely be shared between Detectors and variations.
9. As a library user, I want to add Rules to a Detector with `rules(trip, idling)` (and repeated calls), so that the declaration order is the order I pass them in.
10. As a library user, I want `during(trip)`, `after(trip).unless(fault)`, `count(trip)`, `latest(trip)` and `totalDuration(trip)` to take Rules, so that a misspelled Rule reference does not compile.
11. As a library user, I want `build()` to fail when a Rule references a Rule that was not added to the Detector, so that a reference can never silently stay false.
12. As a library user, I want `build()` to fail when a Rule references a Rule added after it (or itself), so that evaluation order stays well-defined.
13. As a library user, I want `build()` to fail when two added Rules have the same name, so that Events remain attributable.
14. As a library user, I want `build()` to fail when a Rule has no `when`, so that an incomplete Rule is reported at build time as before.
15. As a library user, I want to push values as `Map<Channel, Double>`, so that pushing uses the same handles as the Rules.
16. As a library user, I want values of Channels no Rule uses to be ignored, so that my data may carry more Channels than my Rules need.
17. As a library user, I want `Sample` to hold `Map<Channel, Double>`, so that batch detection uses the same types as streaming.
18. As a library user, I want to read Aggregates with `event.aggregates(speed)`, so that the Channel is checked by the compiler.
19. As a library user, I want `event.aggregates(channel)` to keep throwing for a Channel the Rule does not aggregate, so that a missing `aggregate(...)` is not mistaken for "no data".
20. As a library user, I want `latest(trip).mean(speed)`, `.min(speed)` and `.max(speed)` to take Channels, so that these Expressions are checked too.
21. As a library user, I want `event.is(trip)` to tell me whether an Event was produced by a Rule, so that I do not compare name strings in listeners.
22. As a library user, I want `event.name()` to remain the Rule's name, so that I can store Events and supply them back as History.
23. As a library user, I want `build()` to fail when History contains an Event whose name matches no added Rule, so that a renamed Rule does not silently lose its History.
24. As a library user, I want attributes replaced by plain methods on Events (e.g. `static double km(Event e)`), so that derived values are typed without extra API.
25. As a library user, I want the README and examples to show the new API throughout, so that I learn only one way.

## Implementation Decisions

- **Channel** becomes a public final class extending the existing Channel Expression (moving averages, `rate`, `rising`, `falling`, `age` stay available). It carries a name and a pulse flag; `equals`/`hashCode` use the name only, so a lookup map keyed by Channel matches `Channel.named(x)` created elsewhere. Moving averages derived from a Channel stay plain Channel Expressions, not Channels.
- The static `Conditions.channel(String)` factory is removed.
- **Pulse Channels** are collected at build time from the Channels the Rules reference; the `pulse(...)` method of the Detector builder is removed. If one name appears with both pulse flags, `build()` throws `IllegalArgumentException`. A pulse Channel only pushed but never referenced by any Rule has no effect, as today.
- **Rule** becomes the public, immutable definition that replaces the old public Rule builder: `Rule.named(name)` plus the fluent configuration methods, each returning a new Rule. Semantics of `forSamples`/`forDuration` (they apply to the most recent `when` or `endWhen`) are unchanged. The existing internal runtime class currently named Rule is renamed (e.g. to a name that marks it as a Rule bound to a Detector).
- The Detector builder replaces `rule(name, config)` with `rules(Rule...)`. `build()` throws `IllegalArgumentException` for: a Rule without `when`, duplicate Rule names, a reference to a Rule not added, a reference to itself or to a Rule added later, and History Events of unknown Rules.
- References between Rules are resolved by the referenced Rule's identity within the Detector (by name, since names are unique per Detector); binding keeps its existing "declared before" check but with a Rule handle.
- **Event** keeps `name()`; gains `is(Rule)` (true when the names are equal). The `attributes` component and `withAttributes` are removed. `aggregates` stays a map keyed by Channel name internally or by Channel; the public accessor takes a Channel.
- `push(long, Map<Channel, Double>)`, `push(Map<Channel, Double>)`, `detect(Iterable<Sample>)`, `detectValues(Iterable<Map<Channel, Double>>)`; `Sample(long, Map<Channel, Double>)`. Values are validated as before (non-null, finite).
- Zero steady-state allocation per pushed Sample must still hold: iterating the pushed map must not allocate, and resolving a Channel to its state must not allocate (Channel's hash code is the cached name hash).
- ADR-0003 records the decision; ADR-0002 (explicit `unless`) is unaffected.

## Testing Decisions

- Tests exercise only the public API (build a Detector, push Samples, assert reported Events), as all existing tests do; this is the single seam.
- All existing tests and the ten examples are migrated to the new API; their expected Events must not change, except for attribute output which is replaced by plain methods.
- New tests cover each `build()` failure (unknown Rule reference, later Rule, self, duplicate name, missing `when`, conflicting pulse flag, History of unknown Rule), Channel equality by name in `push`, `event.is(rule)`, and Rule immutability (configuring a derived Rule does not change the original).
- Prior art: the build-time failure tests in the sequence tests (references to later Rules) and the Aggregates tests (unaggregated Channel throws); the performance test's allocation check.

## Out of Scope

- Detecting misspelled Channel names in the data itself (a Sample key no Rule uses is still ignored).
- Keeping the string-based API alongside the new one.
- A typed attribute mechanism.
- Generic Detectors over user enums.

## Further Notes

- The library is `0.1.0-SNAPSHOT` with no releases, so the break is free.
- `CONTEXT.md` terms are unchanged; "Rule" now names the public definition object, which matches the glossary.
