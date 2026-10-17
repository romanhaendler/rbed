# 01: Readable, deterministic descriptions of Conditions and Rules

**What to build:** Conditions and Rules describe themselves as they are written in Java, the way Expressions already do since 0.3.0: e.g. `temp.above(80.0)`, `after(Maintenance).within(7776000000).not()`, and for a Rule its name, `when`, durations, End Condition, `maxGap`, `unless` and aggregated Channels in a fixed order. The description is deterministic (same Rule, same text, on every run and every runtime), built once when the object is created, and is what the Snapshot fingerprint (ticket 03) hashes. See spec: Implementation Decisions "Snapshot format" (fingerprint).

**Blocked by:** None (can start immediately; builds on rbed 0.3.0)

**Status:** ready-for-agent

- [ ] Every Condition (comparisons, `between`, `and`, `or`, `not`, `during`, `after(...).within(...)`, `unless`, and every other factory) has a readable `toString` mirroring its Java form
- [ ] A Rule's `toString` contains everything that defines its behaviour, in a fixed order, and two Rules with equal behaviour-defining configuration have equal descriptions
- [ ] Descriptions are built once (no work per Sample) and are identical on the JVM and in the site's compiled module
- [ ] Tests through the public API pin the description of every construct (prior art: `ExpressionValueTest.anExpressionReadsAsItIsWrittenInJava`)
- [ ] `mvn -q verify` passes; the site's checks pass
