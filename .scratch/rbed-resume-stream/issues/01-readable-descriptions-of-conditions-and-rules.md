# 01: Readable, deterministic descriptions of Conditions and Rules

**What to build:** Conditions and Rules describe themselves as they are written in Java, the way Expressions already do since 0.3.0: e.g. `temp.above(80.0)`, `after(Maintenance).within(7776000000).not()`, and for a Rule its name, `when`, durations, End Condition, `maxGap`, `unless` and aggregated Channels in a fixed order. The description is deterministic (same Rule, same text, on every run and every runtime), built once when the object is created, and is what the Snapshot fingerprint (ticket 03) hashes. See spec: Implementation Decisions "Snapshot format" (fingerprint).

**Blocked by:** None (can start immediately; builds on rbed 0.3.0)

**Status:** done

- [x] Every Condition (comparisons, `between`, `and`, `or`, `not`, `during`, `after(...).within(...)`, `unless`, and every other factory) has a readable `toString` mirroring its Java form
- [x] A Rule's `toString` contains everything that defines its behaviour, in a fixed order, and two Rules with equal behaviour-defining configuration have equal descriptions
- [x] Descriptions are built once (no work per Sample) and are identical on the JVM and in the site's compiled module
- [x] Tests through the public API pin the description of every construct (prior art: `ExpressionValueTest.anExpressionReadsAsItIsWrittenInJava`)
- [x] `mvn -q verify` passes; the site's checks pass

## Comments

- `Condition.toString()` and `Rule.toString()` are the descriptions, built once in the constructor. `After` orders its text `after(R).unless(A, B).within(N)` regardless of call order.
- Rule text: `Rule.named("R").when(C).forSamples(n).forDuration(ms).endWhen(C).forSamples(n).forDuration(ms).maxGap(ms).aggregate(a, b)`. Defaults (0 samples, 0 ms, no maxGap, no aggregates) are omitted; aggregated Channels are sorted by name. The fingerprint (ticket 03) can hash `rule.toString()` directly.
- Conditions and Expressions name a referenced Rule by `rule.name()` only (no nesting). `Rule.toString()` is no longer the bare name; use `name()`.
- Not in the description: Channel kind (Pulse or plain) and the Detector retention limit; ticket 03 must cover them if they matter.
- JVM/JS parity: new `Bridge.describeRules(scenarioId)` export, called from `BridgeTest` for every Scenario, so `parity.test.mjs` compares the compiled output with the JVM.
- Tests: `DescriptionTest`.
