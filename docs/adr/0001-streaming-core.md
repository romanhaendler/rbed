# Streaming core, batch as a loop over it

The Detector processes one Sample at a time and reports Events as their lifecycle advances (Pending, Confirmed, Ended, Discarded). Batch detection over a recorded series is a thin convenience that feeds each Sample to the same core and closes the Detector at the end. We chose this because live use cases (e.g. reporting a load pickup as it happens) need incremental results, and a streaming core can always serve batch use, while a batch core with random access to the whole series cannot be retrofitted to streaming.

## Consequences

- Rules may only look backwards; no Condition can depend on future Samples.
- Memory per Rule must stay bounded, so Aggregates are computed incrementally rather than from stored Samples.
