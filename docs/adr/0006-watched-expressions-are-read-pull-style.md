# An Expression's value is read pull-style from a watched Expression

Users want to log, plot or check the values their Rules compare (a moving average, a rate, a count of Events), and re-implementing rbed's arithmetic drifts from the library. An Expression is now **watched** by registering it with `Detector.builder().watch(...)`, and `detector.value(expression)` returns its value at the latest Sample or time advance as an `OptionalDouble`.

- **Pull, not push.** The caller reads when it wants a value; rbed does not call back with every value. Channel-derived state (moving averages, rate, age) already updates when a Channel updates, and Expressions about Events read the Detector's Event state, so a read is a lookup of the bound Expression. Pushing a Sample does no extra work and allocates nothing more, whether or not anyone reads.
- **Registered, because binding happens once.** An Expression is resolved against a Detector once, in `build()`: a moving average gets its window there and is fed from the first Sample. An Expression that was not bound then has no state to read, so it must be registered before `build()`. Watched Expressions are bound after all Rules, so they may refer to any added Rule, and their Channels count as used.
- **An unwatched read fails fast.** Reading an Expression that was not watched throws `IllegalArgumentException`, in the spirit of [ADR-0003](0003-typed-handles-instead-of-names.md): a forgotten registration must not look like an undefined value. A Channel matches by name, every other Expression by identity, as elsewhere.
- **Undefined is empty.** An undefined value (no Sample yet, a warming-up average, an absent Pulse Channel) is an empty `OptionalDouble`, as `Event.end()` is an empty `OptionalLong` while an Event runs. NaN would be easy to mistake for a number.

## Alternatives considered

- **A callback per moment with all watched values.** Rejected: it costs every push, and most users read rarely. It can be built on top by reading after each push.
- **Read any Expression without registering, binding it on first read.** Rejected: a moving average bound late would lack the values before the read, so the same Expression would differ from the one the Rules see.

## Consequences

- Reading works from EventListener callbacks (the moment being processed) and after `close()` (the last moment).
- Every Expression kind can be watched, but an Expression other than a Channel must be kept to be read, since it matches by identity.
