# Channels and Rules are typed handles, not names

Rules and Conditions refer to Channels and to other Rules through `Channel` and `Rule` objects instead of strings. A misspelled string used to be silently accepted: a Condition on an unknown Channel stayed undefined forever, and a reference to an unknown Rule never became true. With handles, each name is written once, where the handle is created, and the compiler checks every use. Rules are immutable values like Conditions, so "a Rule may only reference Rules declared before it" follows from Java's own scoping instead of a runtime check.

We kept strings only where data crosses the boundary: `Channel.named(...)` and `Event.name()` (the value that is stored and supplied back as History). Channels are equal by name, so external data maps onto them without a registry. We removed Rule attributes instead of typing them: an attribute was only a function of the Ended Event, which a plain method expresses with full type safety and no API.

## Consequences

- `build()` fails if a Rule references a Rule that was not added, or if History contains an Event of an unknown Rule.
- References resolve to the added Rule with the same name, since names are unique per Detector. A differently configured copy of a Rule therefore refers to the added one. A Rule that refers to itself, or to a Rule added after it, is rejected by `build()`.
- Values of Channels no Rule uses are still ignored, so a Sample may carry more Channels than the Rules need; a misspelled Channel name in the data is not detected.
