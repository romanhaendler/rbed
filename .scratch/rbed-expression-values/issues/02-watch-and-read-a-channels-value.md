# 02: Watch and read a Channel's value (tracer bullet)

**What to build:** A user can register Expressions with `Detector.builder().watch(...)` and read their current value with `detector.value(expression)`, an `OptionalDouble` that is empty while the value is undefined. This ticket makes it work end to end for Channels and Expressions derived from a Channel (moving averages over Samples and over a duration, `rate()`, `age()`, arithmetic between Channels), documented in the README and recorded in ADR-0006, with the version raised to 0.3.0. See spec: Solution 3; Implementation Decisions "New public API", "Binding", "No extra evaluation step", "ADR-0006", "README", "Version".

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `Builder.watch(Expression...)` exists; calling it more than once and watching the same Expression twice is harmless
- [x] `Detector.value(Expression)` returns the value at the latest moment processed, or empty while undefined (before the first Sample, during a moving average's warm-up)
- [x] A Channel is matched by name, every other Expression by identity; reading an Expression that was not watched throws `IllegalArgumentException` naming it
- [x] A watched Expression that no Rule uses works, and its Channels count as used (`retainedValues()` reflects them)
- [x] Watching changes no Event: an existing Scenario yields the same Events with and without watched Expressions
- [x] Pushing a Sample does no extra work and allocates nothing more with watched Expressions (existing allocation checks pass with watching enabled)
- [x] JUnit tests through the public API cover a Channel, a moving average during and after warm-up, `rate()`, `age()` and an arithmetic Expression
- [x] ADR-0006 records the pull-style, registered read, the fail-fast unwatched read and empty-for-undefined
- [x] README: a subsection "Reading an Expression's value" in "Conditions and Expressions" with a short example; table of contents updated if needed
- [x] The pom version is 0.3.0 (no release)
- [x] `mvn -q verify` passes

## Comments

- API: `Detector.Builder.watch(Expression...)` (a `LinkedHashSet`, so repeats are harmless) and `Detector.value(Expression)` returning `OptionalDouble` (empty when the bound value is NaN). Watched Expressions live in `Detector.watched`, a `HashMap<Expression, Expression.Bound>`: Channels match by name through `Channel.equals`, all others by identity. Tests: `ExpressionValueTest`; the steady-state allocation check in `PerformanceTest` now watches six Expressions.
- Binding: watched Expressions are bound in the `Detector` constructor right after the Rule loop and before `added`/`addedBefore` are cleared and `pulses` is computed. So `referencedEvent` already accepts any added Rule for a watched Expression (ticket 03 only has to test it), and a watched Pulse Channel is in `pulses`.
- `Detector.bindingRule` was renamed to `binding` and now holds the whole subject for messages: `"Rule X"` or `"Watched Expression " + expression`. The callers (`ChannelExpression.averageOverSamples`, `Window.OverDuration`, `referencedEvent`) use it without a "Rule " prefix; messages for Rules are unchanged. For a watched Expression other than a Channel, `expression.toString()` is still `Object`'s default, so messages (binding errors and the unwatched-read error) name a Channel well but not, say, `x.averageOverSamples(5)`. Ticket 03 decides whether Expressions get a readable `toString`.
- README: only the install section (0.3.0), the new subsection "Reading an Expression's value" at the end of "Conditions and Expressions" and its table-of-contents line. The example reads the value after a push rather than from a listener (a listener needs the Detector before it is built, which made the snippet long); the text says reading from a callback works. The review round added a second example that reads in `onConfirmed` (an application class holding the Detector in a field and implementing `EventListener`), pinned by `ExpressionValueTest.readmeListenerExample`. The "Errors and validation" table does not list the unwatched read or watched-Expression build errors yet (left for ticket 03 to avoid touching more of the README in parallel).
- The site builds and tests against 0.3.0 unchanged (`npm run build`, `npm test`, `test:browser` pass); it does not use the new API yet (ticket 04).
