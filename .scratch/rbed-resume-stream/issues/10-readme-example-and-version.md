# 10: README "Resuming a stream", example test, 0.4.0

**What to build:** The recipe for users. The README gets a section "Resuming a stream" after "Restarts: History" (the three capabilities, what to persist and when, the restart procedures — restore and continue; replay from the Replay Point; an older Snapshot plus replay of the tail — the preconditions, Kafka notes, a link to the example), and the "Errors and validation" table gains the new rejections. A runnable example test in the library's examples plays the whole recipe with an in-memory message log with offsets, an in-memory store and a listener whose side effects go to an output list: for a representative Rule set and every stop offset, all three restart procedures produce exactly the uninterrupted output. The version becomes 0.4.0 in the pom and the README's install section (no release). See spec: Implementation Decisions "README", "Example test", "Version".

**Blocked by:** 07, 08, 09

**Status:** ready-for-agent

- [ ] README section "Resuming a stream" as described, consistent with the Javadoc, ADR-0007 and CONTEXT.md; table of contents updated
- [ ] "Errors and validation" lists every new rejection with its message
- [ ] The example test uses only the public API and no dependencies; for every stop offset, restore-and-continue, replay-from-Replay-Point and Snapshot-plus-tail each produce exactly the uninterrupted output (callbacks and side effects)
- [ ] The README's code in the new section matches the example test (copied from it, or tested like the truncation example)
- [ ] The version is 0.4.0 in the pom and the README's install section; nothing is released or tagged
- [ ] `mvn -q verify` passes; the site's checks pass
