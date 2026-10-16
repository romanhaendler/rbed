# 09: Aggregates and Pulse Channels pages

**What to build:** Two concept pages. Aggregates: clicking an Event shows its per-Channel minimum, maximum and mean, and the Event table gains Aggregates columns, on this page and on every later Scenario page. Pulse Channels: Pulse Channel values are drawn as marks at their Samples, not as a line, since they have no value between Samples; the result keeps absent values absent. See spec: User Stories 19 and 22.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] Selecting an Event shows its Aggregates per Channel
- [ ] The Event table shows Aggregates
- [ ] Pulse Channels are drawn as marks; Samples without a Pulse value show none
- [ ] `run` keeps Pulse Channel values absent where rbed has none
- [ ] JUnit and the Node parity test cover both Scenarios at their defaults
