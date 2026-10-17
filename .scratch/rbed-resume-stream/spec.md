# Spec: resuming a stream after a restart

Status: ready-for-agent

## Problem Statement

An application feeds a Detector from a message stream such as a Kafka topic. When it stops (a deployment, a crash) and starts again, it wants to continue at the next message as if it had never stopped: the same Events, at the same timestamps, with the same flags and Aggregates, each reported exactly once.

rbed keeps a Detector's state in memory. History restores Ended Events for `after`, `unless`, `count`, `totalDuration` and `latest(...).duration()`, but nothing else: not an Event in progress (Pending or Confirmed), not a running duration, not the Samples inside a moving-average window, not `rate()`, `age()` or `maxGap`, not a Rule's knowledge that its `when` Condition has stopped holding, and not the Aggregates that `latest(...).min/max/mean` read (History Events carry none).

The obvious workaround, replaying recent messages into a fresh Detector, is only exact if the replay starts early enough, and the application cannot know how early:

- A Rule whose last Event Ended on its End Condition may still see `when` holding; a fresh Detector would start a new Event (with a Truncated start) at the first replayed Sample, an Event the uninterrupted run never had.
- An Event in progress, a duration, a moving-average window and `latest(...)` Aggregates each reach back a different, data-dependent distance.

The README and the documentation site say nothing about restarting beyond History, so every user has to work this out alone, and most will get it subtly wrong.

## Solution

- rbed tells the application, at any moment, its **Replay Point**: the earliest timestamp from which replaying the same input into a fresh Detector, built with the same Rules and with History of the Events that Ended before that timestamp, reproduces this Detector's state exactly. The application stores the Replay Point together with its stream position after each processed message.
- After a restart, the application builds a fresh Detector with that History, seeks the stream back to the Replay Point, replays up to the last processed message with side effects suppressed (everything there was already reported), and then continues with new messages. Every Event from then on is identical to the uninterrupted run's.
- The README gets a section on resuming a stream, and a runnable example test plays the whole recipe with an in-memory message log and an in-memory store, proving for every possible stop point that the resumed run reports exactly what an uninterrupted run reports.
- The documentation site gets a page "Resuming a stream" next to "Restarts: History": a Scenario with a stop point the reader moves, comparing a naive restart, a restart with History only, and a restart from the Replay Point, against the uninterrupted run.

## User Stories

1. As an application developer, I want to stop and restart my application without losing or duplicating Events, so that alerts and records stay correct across deployments.
2. As an application developer, I want rbed to tell me how far back my stream must be replayed, so that I do not have to derive it from my Rules by hand.
3. As an application developer, I want that point to account for Events in progress, so that a long-running Event keeps its true start after a restart.
4. As an application developer, I want it to account for durations being measured, so that `forDuration` and `forSamples` are reached at the same moment as without the restart.
5. As an application developer, I want it to account for moving-average windows, `rate()`, `age()` and `maxGap`, so that derived values continue exactly.
6. As an application developer, I want it to account for a Rule that has not yet seen its `when` Condition stop holding, so that a restart never creates an Event the uninterrupted run would not have.
7. As an application developer, I want it to account for `latest(...).min/max/mean`, so that Rules reading the latest Event's Aggregates see the same values after a restart.
8. As an application developer, I want it to cost almost nothing to read after every message, so that I can store it with every commit.
9. As an application developer, I want it to be undefined before the first Sample, so that a fresh Detector needs no replay.
10. As an application developer, I want a precise list of what to persist (Ended Events, the Replay Point with the stream position, the last processed position), so that I can design my storage once.
11. As an application developer, I want to know that these must be written atomically (or idempotently), so that a crash between writes cannot desynchronise them.
12. As an application developer, I want Ended Events to have a natural key (Rule name and start), so that writing one twice during a replay is harmless.
13. As an application developer, I want to know which stored Events to pass as History (those Ended before the Replay Point), so that `build()` accepts them and nothing is counted twice.
14. As an application developer, I want to know how to suppress side effects while replaying (up to the last processed position), so that nothing is reported twice.
15. As an application developer, I want to know how time advances fit in (record them like messages, or drive them from Sample timestamps), so that a replay reproduces them.
16. As an application developer, I want to know that a Detector in sampling-rate mode cannot be resumed this way, so that I choose timestamps for streaming.
17. As an application developer, I want to know that one Detector per key and ordered processing per key are required, so that Kafka partitioning maps onto Detectors correctly.
18. As an application developer, I want to know how long my topic's retention must be (at least back to the oldest stored Replay Point), so that a replay never finds data missing.
19. As an application developer, I want a runnable example of the whole recipe, so that I can copy its structure.
20. As an application developer, I want that example to need no Kafka or database, so that it runs anywhere and shows only what matters.
21. As a reader of the documentation site, I want to move a stop point and see that a naive restart and a History-only restart differ from the uninterrupted run while a restart from the Replay Point does not, so that I understand why the Replay Point matters.
22. As a reader of the documentation site, I want to see the Replay Point drawn on the timeline, so that I see how far back the replay reaches and why.
23. As a reader of the documentation site, I want the page to list exactly what to store, so that I can apply it to my own stream.
24. As a coding agent, I want the recipe in the Markdown twin and llms-full.txt, so that I can implement it for a user.
25. As the maintainer, I want the Replay Point proven by a test that compares, for every stop point of several representative Rule sets, the resumed run with the uninterrupted run, so that the guarantee is real.
26. As the maintainer, I want the decision recorded in an ADR, so that the choice of a Replay Point over a serialised snapshot is documented.
27. As the maintainer, I want the new concept in the glossary, so that the README, the Javadoc and the site use one word for it.
28. As the maintainer, I want the feature released as a new minor version, so that users see a new API appeared.

## Implementation Decisions

- **New glossary term (CONTEXT.md): Replay Point.** "The earliest timestamp from which replaying a Detector's input into a fresh Detector, with the Events Ended before it supplied as History, reproduces the Detector's state." Avoid: checkpoint, snapshot, watermark.
- **New public API:** `Detector.replayPoint()` returns an `OptionalLong`: empty before the first Sample (nothing to replay); otherwise the Replay Point at the latest moment processed. Reading it allocates nothing beyond the result and does no work proportional to history; the Detector keeps what it needs up to date as it processes Samples. The name is the decision; the Javadoc states the guarantee and its preconditions.
- **What the Replay Point covers** (the minimum over all of these, at the latest moment processed): the start of every Event in progress (Pending or Confirmed) and of its pending End Condition duration; for every Rule that has not yet seen its `when` Condition stop holding since its last Event, the last moment it saw it not holding (or the first Sample, if never); the oldest Sample still inside any moving-average window (by Samples or by duration); the previous Sample of every Channel read by `rate()`, `age()` or `maxGap`; the start of the latest Event of every Rule read through `latest(...).min/max/mean`; the start of the latest Event of every Rule read through `latest(...)` while that Event is in progress. Whatever the implementation finds necessary beyond this list to pass the property test is part of the decision too. History Events restored at the restart cover everything else (`after`, `unless`, `count`, `totalDuration`, `latest(...).duration()`).
- **Preconditions** (documented, not enforced beyond what rbed already checks): the same Rules (same names and definitions) and Detector options; timestamps, not sampling-rate mode (a Detector with a sampling rate has no meaningful Replay Point and returns empty, documented); the same input replayed, including time advances (the application records advances like messages or derives them from Sample timestamps); History contains exactly the stored Events that Ended before the Replay Point.
- **No snapshot/restore.** A serialised Detector state was considered and rejected for now: it would need a stable format across versions and Rule changes, while the Replay Point needs nothing but the input the application already has. ADR-0007 records this, the definition and the preconditions.
- **README:** a section "Resuming a stream" after "Restarts: History": what to persist (Ended Events keyed by Rule name and start; the Replay Point with the stream position; the last processed position; written in one transaction or idempotently), how to restart (History of Events Ended before the Replay Point, seek, replay with side effects suppressed up to the last processed position, continue), the preconditions, Kafka notes (one Detector per key, ordered per partition, retention, seeking by timestamp or by stored offset) and a link to the example test.
- **Example test** (in the library's examples, like the domain examples): an in-memory message log with offsets stands in for the topic, an in-memory store for the database, a listener whose side effects go to an output list. It runs a representative Rule set uninterrupted, then for every stop offset runs: process up to the stop, "crash" (drop the Detector), restart by the recipe, process to the end; the combined output equals the uninterrupted output. It uses only the public API and no dependencies.
- **Documentation site:** a Guide page "Resuming a stream" after "Restarts: History", with a Scenario whose parameters are the stop point and a choice of restart (naive, History only, Replay Point). The bridge result carries the Replay Point at the stop and the uninterrupted run's Events for comparison; the chart marks the stop and the Replay Point and shows which Events differ. Built with the site's existing recipe ("Adding a page"), so prerendering, twins and llms files follow automatically.
- **Version:** 0.4.0 (after 0.3.0). Releasing follows RELEASING.md.

## Testing Decisions

- **Good tests compare observable output:** the Events (names, start, Confirmed, end, Truncated flags, Aggregates) and listener callbacks of a resumed run against an uninterrupted run. They never inspect the Detector's internals.
- **One seam for the guarantee: the library's public API.** A property-style JUnit test runs, for each of several representative Rule sets, every stop point and asserts resumed == uninterrupted. The Rule sets together cover: durations (`forDuration`, `forSamples`), End Conditions with `when` still holding at the end, `maxGap` and data gaps, moving averages over Samples and over a duration, `rate()`, `age()` with time advances, `after(...).within(...)`, `count(...).within(...)`, `totalDuration`, `latest(...).duration()` and `latest(...).max(...)`, Pulse Channels, and History supplied at the very first build. The same test asserts that a naive replay from a later point (e.g. one Sample after the Replay Point) does differ for at least one stop point per Rule set where it should, so the Replay Point is not trivially "the beginning".
- **Unit-level checks through the API:** `replayPoint()` is empty before the first Sample and in sampling-rate mode; it never moves backwards in time while nothing new reaches back; it does not allocate per Sample (existing allocation checks).
- **Site:** the existing bridge seam (BridgeTest + parity + SourceTest) for the new Scenario: at the defaults the Replay Point restart equals the uninterrupted run and the naive restart does not; the browser smoke tests cover the page.
- **Prior art:** the domain example tests (an Event list per run), `EndConditionTest` (restart rule, from rbed-expression-values ticket 01), `TimeAdvanceTest`, `RetentionTest`, the History tests, `PerformanceTest`'s allocation checks; on the site, the History page's Scenario and BridgeTest's restart tests.

## Out of Scope

- A Kafka (or any messaging/database) dependency in the library or its tests.
- Serialising or restoring a Detector's state (snapshot/restore).
- Resuming in sampling-rate mode.
- Changing Rules between the stop and the restart (the guarantee assumes the same Rules).
- Exactly-once delivery machinery: the recipe states the requirements (atomic or idempotent writes); implementing a transactional outbox is the application's.
- Reading Conditions or Rule states (beyond the Replay Point) from the Detector.

## Further Notes

- This spec grew from the question "what must I persist to resume a Kafka stream without interruption?" (October 2026). The analysis showed that a replay is the only way with rbed 0.2/0.3 and that its start cannot be derived safely by the application: a Rule whose `when` still holds after its Event Ended on an End Condition would create an extra Event in a fresh Detector, and `latest(...)` Aggregates are not carried by History.
- Depends on nothing from rbed-expression-values except that it builds on 0.3.0; it can start once 0.3.0 is released.
- If the property test shows a case the Replay Point cannot make exact with reasonable bookkeeping, record it in ADR-0007 and the Javadoc as a precondition rather than weakening the guarantee silently.
