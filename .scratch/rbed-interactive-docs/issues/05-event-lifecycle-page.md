# 05: Event lifecycle page

**What to build:** A page on the Event lifecycle where Pending, Confirmed, Ended and Discarded are visible as distinct marks on the time axis. The bridge's result gains each Event's Confirmed timestamp and every Discarded Pending Event with its start and the timestamp it was Discarded, recorded by an EventListener with the same callbacks as the test Recorder. A duration control lets the reader raise `forDuration` until a short Event turns from Confirmed into Discarded. The Event table gains a Confirmed column. See spec: User Stories 6 to 8; Implementation Decisions "The result of run".

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] `run` returns the Confirmed timestamp of each Event and every Discarded Pending Event with its start and Discarded timestamp
- [ ] The chart marks Pending start, Confirmed, Ended and Discarded distinctly, and the text alternative names them
- [ ] JUnit: raising the lifecycle Scenario's duration past a short Event turns it from Confirmed into Discarded
- [ ] The Node parity test covers the lifecycle Scenario at its defaults and at the pinned duration change
- [ ] The Event table shows the Confirmed timestamp separately from the start
