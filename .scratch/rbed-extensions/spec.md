# Spec: rbed extensions (gaps from the examples)

Status: ready-for-agent

## Problem Statement

Ten domain examples (see `.scratch/rbed-core/gaps.md`) showed things users need that the library cannot express. Today they work around them in listeners or by pre-computing Channels. The workarounds cannot drive detection, and some are unsafe (a silent sensor cannot raise an alarm while it stays silent) or wrong (sample-weighted means under irregular sampling). Separately, performance tests showed that keeping Aggregates for every Channel in every running Event costs about 1 ns per Event and Channel per Sample, which dominates with many Channels.

## Solution

Add, in the same fluent style and without runtime dependencies:

- Aggregates only for the Channels a Rule uses, plus explicitly requested ones.
- Time advancing without Samples, and the age of a Channel's value.
- Values of earlier or running Events in Conditions.
- Arithmetic on Expressions.
- Counting Events in a window, and the total duration of Events.
- History functions (`rate`, `rising`, `falling`, averages) on smoothed series.
- Time-weighted mean and integral in Aggregates.
- Pulse Channels.

## User Stories

1. As a library user, I want Aggregates kept only for the Channels my Rule uses, so that many Channels do not slow detection down.
2. As a library user, I want to request Aggregates for additional Channels with `.aggregate("x")`, so that I can characterise an Event by Channels its Conditions do not mention.
3. As a library user, I want an immediate exception when I read Aggregates of a Channel the Rule does not aggregate, so that a missing declaration is not mistaken for "no data".
4. As a library user, I want to tell the Detector that time has passed without data (`advanceTo(t)`), so that timeouts and durations fire while a sensor is silent.
5. As a library user, I want the age of a Channel's last value as an Expression, so that I can write "sensor silent for 10 s" as a Rule.
6. As a library user, I want `advanceTo` not to count as a Sample, so that `forSamples` and Aggregates keep their meaning.
7. As a library user, I want to use the mean, minimum, maximum and duration of the latest Event of another Rule in a Condition, so that I can detect things like "weight fell below half of the lifted load".
8. As a library user, I want the latest Event to include a running one, so that I can compare against the Event in progress.
9. As a library user, I want referenced Aggregates to be kept automatically by the referenced Rule, so that I don't have to declare them twice.
10. As a library user, I want to add, subtract, multiply and divide Expressions and constants, and take absolute values, so that I can compare differences, ratios and magnitudes.
11. As a library user, I want division by zero and other non-finite results to be undefined, so that they never make a Condition true.
12. As a library user, I want to count the Events of a Rule that were Confirmed within a time window, so that I can detect "more than 3 starts per hour".
13. As a library user, I want History Events to count as well, so that counts survive restarts.
14. As a library user, I want the total duration of a Rule's Events since the Detector started, including a running one, so that I can limit cumulative time above a threshold.
15. As a library user, I want `rate`, `rising`, `falling` and averages on a moving average, so that noisy signals can be smoothed before taking a slope.
16. As a library user, I want a time-weighted mean and an integral (value x seconds) per Channel in Aggregates, so that irregular sampling does not bias results and I can compute quantities like distance.
17. As a library user, I want to declare Pulse Channels whose value exists only in the Sample carrying it, so that one-off markers do not read as permanently set.
18. As a library user, I want the examples to use these features instead of workarounds, so that they show idiomatic use.

## Implementation Decisions

- **Aggregated Channels (A).** Each Rule aggregates the Channels referenced anywhere in its `when` and `endWhen` Conditions, the Channels named by `.aggregate(String...)` on its RuleBuilder, and Channels requested by other Rules' `latest(...)` references (story 9). Collected at build time. `Event.aggregates(channel)` throws `IllegalArgumentException` for a Channel that the producing Rule does not aggregate. For a Channel that is aggregated but had no value yet, it returns count 0 as before. `Event.aggregates()` (the map) contains the aggregated Channels that had values. The Accumulator iterates only the Rule's aggregated Channels.
- **Time advance (1).** `Detector.advanceTo(long timestamp)` (timestamped mode only; `IllegalStateException` with a sampling rate) processes the passage of time without a Sample. Timestamp rules are the same as for `push` (strictly increasing). The Rules are evaluated at that timestamp with all Channels holding their values. The max-gap check applies. `forDuration` and End-Condition durations can be reached. `forSamples` counts, Sample-based averages and Aggregates (`count`, `mean`, `min`, `max`) are not affected. Time-weighted values do extend up to the advance. A Pending or running Event can start, be Confirmed, Discarded or Ended on an advance. Once an advance exceeds a Rule's `maxGap`, that Rule stays idle until the next Sample, because held values are stale. `close()` truncates at the latest Sample or advance.
- **Channel age (1).** `channel("x").age()` is an Expression: milliseconds since the Channel's last value, measured at the current Sample or advance. It is undefined before the first value.
- **Latest Event (2).** `Conditions.latest(String eventName)` returns an `EventExpression` factory with `mean(channel)`, `min(channel)`, `max(channel)` and `duration()` (ms; for a running Event, up to now). These refer to the most recent Event of that name that was Confirmed, whether running or Ended, and are undefined if there is none. History Events have no Aggregates, so channel values from History are undefined, while `duration()` works. Rule-reference validation (only earlier Rules or foreign names) applies.
- **Arithmetic (3).** `Expression.plus/minus/times/dividedBy(Expression | double)` and `abs()`. The result is undefined if any operand is undefined or the result is not finite.
- **Counting (4).** `Conditions.count(String eventName)` is an Expression: the number of Events of that name Confirmed since the Detector started, History included. `.within(long ms | Duration)` narrows it to Events Confirmed at most `window` before now. Memory is bounded by the number of Events in the window.
- **Total duration (5).** `Conditions.totalDuration(String eventName)` is an Expression in ms: the sum of (end − start) of all Ended Events of that name since the Detector started, History included, plus (now − start) of a running Confirmed Event.
- **Smoothed series (6).** `averageOverSamples` and `averageOverDuration` return a `ChannelExpression`. That is a series updated whenever its source Channel receives a value, and it supports the same history functions (`rising`, `falling`, `rate`, `age`, averages). A series' previous value is its value at the source's previous update. It is undefined while warming up.
- **Time-weighted Aggregates (7).** `Aggregates` gains `timeWeightedMean` and `integral` (value × seconds). Under sample-and-hold, each value holds from its Sample until the next Sample, advance or the Event's end, whichever comes first. The integral runs from the Event's start to its end, or to the last Sample or advance for snapshots. The time-weighted mean is the integral divided by that span in seconds, and undefined (NaN) for a zero span. Channels without a value at the Event's start contribute from their first value.
- **Pulse Channels (8).** `Detector.Builder.pulse(String... channels)`. In every Sample that does not carry a Pulse Channel, its value is undefined. History functions and averages of a Pulse Channel only see the Samples that carry it.
- **Compatibility.** `Aggregates` is a record, so adding components changes its constructor. Tests that construct it are updated. Everything else is additive.

## Testing Decisions

- Same single seam: the public API (Detector builder, `push`, `advanceTo`, `close`, `detect`, listener). A test class per ticket area.
- Expected values come from worked examples in the test comments, not recomputation.
- The performance suite gains a scenario for (A) and must keep zero steady-state allocation, including with the new Expressions. The 50 Rules x 20 Channels floor is raised once (A) lands.
- The examples are updated to replace their workarounds (ticket 10), and `gaps.md` records what was resolved.

## Out of Scope

- Timers inside the library; the caller drives `advanceTo`.
- Windowed total duration (only "since start").
- Aggregates of History Events.
- Arbitrary history functions on arbitrary Expressions. Only Channel series and their averages are supported.

## Further Notes

Vocabulary: `CONTEXT.md` (new term: Pulse Channel). All files in English.
