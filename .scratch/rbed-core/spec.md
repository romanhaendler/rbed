# Spec: rbed core library

Status: ready-for-agent

## Problem Statement

Developers working with time series (sensor data from machines, lab recordings, telemetry) repeatedly need to detect meaningful occurrences in them: a value crossing a threshold, staying there for a while, or happening only after something else has happened. Today they hand-write ad-hoc state machines for each case. These are fiddly to get right (durations, gaps in the data, noisy signals flickering around a threshold, recordings that start or stop mid-occurrence), hard to read, and hard to combine. Existing CEP or streaming frameworks are heavyweight and pull in many dependencies. There is no small, dependency-free Java library that lets you declare such Rules in a few readable lines and run them either live or over a recorded series.

## Solution

rbed is a dependency-free Java 17 library. The user declares Rules with a fluent Java API: Conditions over named Channels, combined with timing logic (minimum duration in Samples or in time, separate End Conditions, maximum gaps) and ordering logic (after another Event, unless some Event occurred in between, within a time window, during another running Event). A Detector built from these Rules receives Samples one at a time and reports each Event's lifecycle (Pending, Confirmed, Ended, Discarded) to a listener. The same Detector can process a whole recorded series in one call and return the detected Events. Every Event carries its start and end, Truncated flags when observation began or stopped mid-Event, per-Channel Aggregates, and optional user-computed attributes. That makes it possible, for example, to tell which load was lifted and later set down by a crane.

## User Stories

1. As a library user, I want to add rbed without pulling in any transitive runtime dependencies, so that my application's dependency tree stays clean.
2. As a library user, I want to declare a Rule in a few lines of fluent Java, so that detection logic is readable and reviewable.
3. As a library user, I want to give each Rule a name, so that the Events it produces are identifiable.
4. As a library user, I want to write a Condition that a Channel is above a constant, so that I can detect simple threshold crossings.
5. As a library user, I want to write a Condition that a Channel is below a constant, so that I can detect drops.
6. As a library user, I want to write a Condition that a Channel lies between two constants, so that I can detect values in a band.
7. As a library user, I want to compare one Channel against another Channel, so that I can use dynamic limits (e.g. a load limit that depends on boom length).
8. As a library user, I want to combine Conditions with and, or and not, so that I can express compound situations.
9. As a library user, I want a Condition that a Channel is rising or falling compared to its previous value, so that I can detect trends.
10. As a library user, I want to use a Channel's rate of change per second in a Condition, so that I can detect fast changes.
11. As a library user, I want to use a moving average over the last N Samples or over a time span in a Condition, so that noisy signals don't cause false detections.
12. As a library user, I want history-based values that are not yet defined (too few Samples so far) to make the Condition false, so that the warm-up phase never produces spurious Events.
13. As a library user, I want to require that Conditions hold for at least N consecutive Samples, so that short spikes are ignored.
14. As a library user, I want to require that Conditions hold for at least a time duration, so that detection works with irregular sampling.
15. As a library user, I want to give a Rule a separate End Condition with its own duration, so that Events with different start and end thresholds (hysteresis) are detected once rather than flickering.
16. As a library user, I want an Event without an End Condition to end as soon as its Conditions stop holding, so that the simple case needs no extra configuration.
17. As a library user, I want to set a maximum gap between Samples for a Rule, so that a long data outage doesn't silently count as the Condition holding.
18. As a library user, I want a Channel's last known value to hold until its next Sample, so that Channels sampled at different rates can be combined.
19. As a library user, I want to push Samples that contain only some of the Channels, so that I can feed independent sensors as their data arrives.
20. As a library user, I want a Condition on a Channel that has never received a value to be false, so that missing data never triggers Events.
21. As a library user, I want to write a Rule that fires only after an Event of another Rule has occurred, so that I can detect sequences.
22. As a library user, I want to restrict such a sequence with unless(...) Events that must not have occurred in between, so that interrupted sequences don't match.
23. As a library user, I want to restrict such a sequence to a time window, so that stale predecessor Events don't match.
24. As a library user, I want a Condition that another Rule's Event is currently Confirmed and not yet Ended, so that I can detect things that happen during another Event (e.g. overload during a lift).
25. As a library user, I want to be notified when an Event becomes Pending, so that I can react early if I choose.
26. As a library user, I want to be notified when an Event is Confirmed, so that I can act as soon as detection is certain.
27. As a library user, I want to be notified when an Event Ends, with its complete start, end and Aggregates, so that I can process the finished occurrence.
28. As a library user, I want to be notified when a Pending Event is Discarded, so that I can retract any early reaction.
29. As a library user, I want to implement only the listener callbacks I care about, so that simple use stays simple.
30. As a library user, I want each Event to carry per-Channel minimum, maximum and mean over its duration, so that I can characterise it (e.g. which load was lifted).
31. As a library user, I want to attach named attributes computed from an Event's Aggregates, so that I can classify Events inside the Rule definition.
32. As a library user, I want an Event that was already in progress at the first Sample to be flagged as having a Truncated start, so that I know its start and Aggregates are incomplete.
33. As a library user, I want to close the Detector and have running Events reported as Ended with a Truncated end, so that no Event is silently lost when data stops.
34. As a library user, I want Pending Events to be Discarded when the Detector is closed, so that unconfirmed detections are not reported as real.
35. As a library user, I want to supply History (Events known from before the Detector started), so that sequence Rules keep working across application restarts.
36. As a library user, I want to push Samples with explicit millisecond timestamps, so that I can process irregular or real-time data.
37. As a library user, I want to configure a sampling rate and push Samples without timestamps, so that regularly sampled data needs no timestamp bookkeeping.
38. As a library user, I want to run a whole recorded series through a Detector in one call and get the list of Events, so that offline analysis is a one-liner.
39. As a library user, I want duration parameters to accept either milliseconds or java.time.Duration, so that I can use whichever is convenient.
40. As a library user, I want an immediate, clear exception when timestamps go backwards or repeat, so that data errors are not hidden.
41. As a library user, I want an immediate, clear exception when a Rule references a Rule declared after it, or itself, so that circular Rules can't exist.
42. As a library user, I want an immediate, clear exception on NaN or infinite values, so that corrupt data is not interpreted.
43. As a library user, I want an exception when pushing after close, so that misuse is caught.
44. As a library user, I want only one Event per Rule to be in progress at a time, so that results are unambiguous.
45. As a library user, I want the Detector's behaviour to be deterministic for the same input, so that tests and reprocessing are reliable.
46. As a library user, I want the Detector's memory use to stay bounded regardless of how long Events last, so that it can run indefinitely on a live stream.
47. As a library user, I want the library to be a proper JPMS module, so that it works cleanly in modular applications.

## Implementation Decisions

- **Language and build:** Java 17, Maven, JPMS module `rbed`, package `rbed`, groupId `io.github.rbed` (placeholder, to be renamed before publishing). Zero runtime dependencies. JUnit 5 is a test-scope dependency only.
- **Streaming core (ADR-0001):** the Detector processes one Sample per call. Batch detection feeds each Sample to the same core and closes the Detector at the end. No Rule may look at future Samples. Aggregates and history-based values are computed incrementally with bounded memory (a moving average over N Samples or a time span keeps only that window).
- **Public API surface** (all in package `rbed`):
  - `Detector`: created via `Detector.builder()`. Has `push(long timestampMillis, Map<String, Double> values)`, `push(Map<String, Double> values)` (only when a sampling rate is configured), `close()`, and `detect(...)` for batch over timestamped Samples or, with a sampling rate, over plain value maps. `detect` pushes everything, closes, and returns all Ended Events in the order they Ended (ties in Rule declaration order). A Detector is single-use: pushing after close, or calling detect on a used Detector, throws `IllegalStateException`. Not thread-safe (documented).
  - `Detector.Builder`: `rule(String name, UnaryOperator<RuleBuilder>)`, `listener(EventListener)`, `history(Collection<Event>)`, `samplingRate(double hz)`, `build()`.
  - `RuleBuilder`: `when(Condition)`, then optional `forSamples(int)` / `forDuration(long ms | Duration)`, `endWhen(Condition)` with its own optional `forSamples` / `forDuration`, `maxGap(long ms | Duration)`, `attribute(String name, Function<EventAggregates, Object>)`. Duration modifiers apply to the most recent `when` / `endWhen`.
  - `Conditions` (static factory, intended for static import): `channel(String)` returns a value expression. Value expressions have `above`, `below` and `between` against constants or other expressions, plus `rising()`, `falling()`, `rate()` (per second) and `movingAverage(int samples | long ms | Duration)`. The last two return expressions again. Also `after(String eventName)` returning a sequence condition with `unless(String...)` and `within(long ms | Duration)`, and `during(String eventName)`. `Condition` has `and`, `or` and `not`.
  - `Event`: immutable. Holds `name` (the Rule name), `start`, `end` (absent until Ended), `truncatedStart`, `truncatedEnd`, `aggregates(String channel)` returning min, max, mean and count, and `attributes()` (a map; populated on End).
  - `EventListener`: interface with default no-op methods `onPending`, `onConfirmed`, `onEnded` and `onDiscarded`, each receiving an `Event` snapshot.
  - `Sample`: a record of timestamp and values, for batch input.
- **Sample semantics:** timestamps are `long` milliseconds and must strictly increase. Otherwise `IllegalArgumentException`. With a sampling rate, timestamp = round(index × 1000 / hz). Mixing timestamped and untimestamped pushes on one Detector throws. A Sample may contain any subset of Channels. Each Channel keeps its last known value (sample and hold). NaN or infinite values throw `IllegalArgumentException`.
- **Condition evaluation:** Conditions are evaluated once per Sample against the current held values. A comparison on a Channel with no value yet, or on a history-based expression that is not yet defined, is false (and `not` of an undefined comparison is also false, i.e. undefined propagates as false at the comparison level, not flipped). `rising` and `falling` compare with the value from the previous Sample that contained that Channel.
- **Lifecycle per Rule** (at most one Event in progress per Rule):
  - Idle → Pending on the first Sample where the `when` Condition holds. Start = that Sample's timestamp. If that Sample is the Detector's first Sample, the Event is Truncated at the start.
  - Pending → Confirmed on the first Sample where the `when` Condition has held for the required count (N consecutive Samples, including the first) or duration (current timestamp − start ≥ duration). Without a duration requirement, Pending and Confirmed are both reported on the same Sample.
  - Pending → Discarded on the first Sample where the `when` Condition no longer holds.
  - Confirmed → Ended: without an End Condition, on the first Sample where the `when` Condition no longer holds. End = that Sample's timestamp. With an End Condition, the end phase begins on the first Sample where the End Condition holds. Once it has held for its required count or duration, the Event Ends with end = the timestamp where the end phase began. If the End Condition stops holding before that, the end phase resets and the Event continues.
  - Aggregates include every Sample from start up to, but excluding, the Sample at which the end timestamp was set. Attributes are computed from them on End.
  - Max gap: if the time between consecutive Samples exceeds the Rule's `maxGap`, a Pending Event is Discarded, and a Confirmed Event Ends Truncated at the end with end = the timestamp of the last Sample before the gap. Evaluation then continues normally with the current Sample.
  - Close: a Confirmed Event Ends Truncated at the end with end = the last Sample's timestamp. A Pending Event is Discarded.
- **Sequences (ADR-0002):** `after(A)` holds at a Sample if an Event named A has Ended at or before this Sample's timestamp. With `unless(C, ...)`, no Event with one of those names may have been Confirmed after the most recent A Ended. With `within(w)`, the current timestamp − end of the most recent A must be ≤ w. `during(A)` holds while an Event named A is Confirmed and not yet Ended. Rules are evaluated in declaration order on every Sample, so a Rule sees the effects of earlier Rules on the same Sample. A Rule may reference only Rules declared before it, or names that no Rule produces (those can come only from History). Referencing itself or a later Rule throws `IllegalArgumentException` at build time.
- **History:** Events passed to `history` are treated as Ended before the first Sample. They count for `after` and `unless` and are not reported to the listener.
- **Listener:** called synchronously from `push` and `close` in lifecycle order, and in Rule declaration order within a Sample. Events given to the listener are immutable snapshots.

## Testing Decisions

- **One seam:** the public API. Every test builds a Detector with the builder, pushes Samples (or calls `detect`) and asserts on the Events and listener calls it produces. Internal classes (state machine, expressions, aggregators) are never tested directly. This keeps tests valid through any internal refactoring.
- A good test states a scenario in domain terms (a short series of Samples) and asserts externally visible outcomes: which Events, with what start, end, flags, Aggregates and attributes, and which lifecycle callbacks in which order. It never asserts on internal state.
- A tiny recording listener in test code captures `(callback, event)` pairs so the lifecycle order can be asserted.
- Coverage targets, one focused test class per area: threshold Conditions and combinators; durations in Samples and time; End Conditions and hysteresis; max gap; sample-and-hold with partial Samples; history-based expressions and warm-up; Truncated start and end; `after` / `unless` / `within` / `during` and build-time reference validation; History; sampling-rate mode; batch `detect`; input validation errors. An end-to-end crane scenario (lift, overload during lift, set-down with load classified via an attribute) serves as the living example.
- No prior art exists in the codebase; this spec establishes the pattern.

## Out of Scope

- Non-numeric Channel values (strings, enums). Encode them as numbers.
- A textual rule format (JSON, YAML or a custom DSL) and any parser.
- Snapshotting or restoring full Detector state. History covers restarts.
- Thread safety and parallel evaluation within one Detector.
- Out-of-order or late Samples, and reordering buffers.
- Overlapping Events of the same Rule.
- Timers that confirm or end Events without a new Sample arriving.
- "The most recent Event of any Rule was A" semantics (rejected in ADR-0002).
- Publishing to Maven Central and final coordinates or license.

## Further Notes

- Domain vocabulary is defined in `CONTEXT.md`. Use these terms in code, Javadoc and tests.
- All files, code, comments and docs are written in English (project rule in `CLAUDE.md`).
