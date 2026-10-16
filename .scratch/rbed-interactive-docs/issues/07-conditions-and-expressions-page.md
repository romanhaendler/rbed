# 07: Conditions and Expressions page

**What to build:** A concept page where the reader picks an Expression (rate, moving average over Samples, moving average over a duration, arithmetic between Channels) and sees its value at every Sample plotted as an extra line next to the Channel it is computed from, with the Condition's threshold. Where the Expression is undefined (for example during a moving average's warm-up), the line is absent and the region is marked, and no Event starts there. The bridge's result gains, for Scenarios that ask for it, the Expression's values per Sample. See spec: User Stories 13 and 14; Implementation Decisions "The result of run".

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] Choosing an Expression shows its values as a line, with controls for its parameter (window size, Sample count)
- [ ] Undefined stretches are marked and carry no Event
- [ ] `run` returns the Expression's values per Sample, undefined where rbed's Expression is undefined
- [ ] JUnit pins the undefined warm-up length for one moving average, and the Node parity test covers each Expression
