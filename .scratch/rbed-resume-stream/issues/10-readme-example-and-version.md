# 10: README "Resuming a stream", example test, 0.4.0

**What to build:** The recipe for users. The README gets a section "Resuming a stream" after "Restarts: History" (the three capabilities, what to persist and when, the restart procedures — restore and continue; replay from the Replay Point; an older Snapshot plus replay of the tail — the preconditions, Kafka notes, a link to the example), and the "Errors and validation" table gains the new rejections. A runnable example test in the library's examples plays the whole recipe with an in-memory message log with offsets, an in-memory store and a listener whose side effects go to an output list: for a representative Rule set and every stop offset, all three restart procedures produce exactly the uninterrupted output. The version becomes 0.4.0 in the pom and the README's install section (no release). See spec: Implementation Decisions "README", "Example test", "Version".

**Blocked by:** 07, 08, 09

**Status:** done

- [x] README section "Resuming a stream" as described, consistent with the Javadoc, ADR-0007 and CONTEXT.md; table of contents updated
- [x] "Errors and validation" lists every new rejection with its message
- [x] The example test uses only the public API and no dependencies; for every stop offset, restore-and-continue, replay-from-Replay-Point and Snapshot-plus-tail each produce exactly the uninterrupted output (callbacks and side effects)
- [x] The README's code in the new section matches the example test (copied from it, or tested like the truncation example)
- [x] The version is 0.4.0 in the pom and the README's install section; nothing is released or tagged
- [x] `mvn -q verify` passes; the site's checks pass

## Comments

- New: README section "Resuming a stream" (after "Restarts: History"; subsections What to persist, Restarting, Preconditions, Kafka notes), table-of-contents entry, six new rows in "Errors and validation". Version 0.4.0 in `pom.xml` and the README install section (the site reads the pom; nothing released or tagged).
- Example: `examples/ResumingAStreamExample` (topic = list, `Store`, `Consumer` listener writing to an output list). Rules: Overheat (End Condition on `load` while `temp` still holds `when`), Warm (moving average, `forSamples`), Repeated (`count` within). For every stop (0..120) the three tests (restore with a Snapshot after every message, replay from the Replay Point, older Snapshot every 7 messages plus tail) equal the uninterrupted output, which is also asserted as a literal. The README code block is copied verbatim from the region between `README-BEGIN` and `README-END` in the test (indentation reduced by two spaces).
- For the site page (ticket 11): the recipe's pieces are `Store`, `Consumer.fresh/restore/replay`; with no Snapshot yet, restore falls back to a full replay; with an empty Replay Point the replay starts at offset 0 with no History.
- Checks: `mvn -q verify`, site `npm ci`, `npm run build`, `npm test` (280), `CI=1 PORT=4871 npm run test:browser` (21) pass.
