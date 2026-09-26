# rbed

Rule-based event detection: finds Events in multi-channel time series by evaluating declarative Rules, either on a live stream or on a recorded series.

## Language

**Sample**:
The values of one or more Channels at a single timestamp.
_Avoid_: Measurement, data point, record

**Channel**:
A named signal within a time series, such as "weight" or "hook height".
_Avoid_: Signal, series, column, variable

**Condition**:
A statement about a single Sample that is either true or false, such as "weight above 500".
_Avoid_: Predicate, check, filter

**Rule**:
One or more Conditions combined with timing logic (duration, ordering) that produces Events when satisfied.
_Avoid_: Pattern, trigger

**Event**:
A detected occurrence with a start and an end timestamp; a point-in-time Event has start equal to end.
_Avoid_: Detection, match, alarm

**Detector**:
The runtime object that receives Samples and evaluates Rules against them to emit Events.
_Avoid_: Engine, processor, evaluator

**End Condition**:
An optional Condition, with its own duration, that ends an Event; without one, an Event ends when its Rule's Conditions stop holding.
_Avoid_: Stop condition, reset condition

**Aggregates**:
Per-Channel minimum, maximum and mean over the Samples of an Event, from its start to its end.
_Avoid_: Statistics, summary

**Truncated**:
Marks an Event whose true start or end lies outside the observed data, because observation began or stopped while it was in progress.
_Avoid_: Partial, incomplete, clipped

**History**:
Events known from before the Detector started, supplied so that Rules depending on earlier Events work across restarts.
_Avoid_: Seed, initial state, snapshot

### Event lifecycle

**Pending**:
An Event whose Conditions hold but whose required duration has not been reached yet.
_Avoid_: Candidate, tentative

**Confirmed**:
An Event whose Conditions held for the required duration; only Confirmed Events count as detected.
_Avoid_: Active, triggered, fired

**Ended**:
A Confirmed Event whose end has been reached.
_Avoid_: Closed, finished, completed

**Discarded**:
A Pending Event whose Conditions stopped holding before it was Confirmed.
_Avoid_: Cancelled, aborted, rejected
