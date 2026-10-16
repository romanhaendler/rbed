# 10: Sampling rate, silent sensors and History pages

**What to build:** Three concept pages for behaviour beyond plain pushing. Batch and sampling rate: a toggle between push-based and sampling-rate mode on the same Samples, showing how durations are measured differently. Silent sensors: a Scenario whose sensor stops reporting while time is advanced with `advanceTo`, showing `age()` and Events ending without Samples. History: a restart in the middle of the data, with a toggle to supply History, showing a Rule that depends on earlier Events behaving differently with and without it. The Scenario contract gains what these need (a sampling mode, timestamps to advance to, a restart point with History). See spec: User Stories 20, 21, 23.

**Blocked by:** 05

**Status:** ready-for-agent

- [ ] Toggling sampling-rate mode changes the Events as rbed's README describes
- [ ] The silent-sensor page shows time advancing without Samples and an Event ending there
- [ ] Toggling History on the restart changes the dependent Rule's Events
- [ ] JUnit pins each toggle's effect, and the Node parity test covers both states of each page
