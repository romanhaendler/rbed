# 06: End Conditions and data gaps pages

**What to build:** Two concept pages. End Conditions: a noisy Channel near a threshold, with controls to move the End Condition's threshold apart from the start threshold (hysteresis removes flapping Events) and to give the End Condition its own duration. Data gaps: a Scenario with a gap in its Samples, with controls to move the gap and to change `maxGap`, showing when an Event is ended at the gap or kept across it. Truncated start and end are marked on the bands. See spec: User Stories 9 to 12.

**Blocked by:** 05

**Status:** ready-for-agent

- [ ] Moving the End Condition's threshold apart from the start threshold reduces the number of Events on the noisy Channel
- [ ] The End Condition's duration control delays the end of Events visibly
- [ ] Moving the gap or changing `maxGap` switches an Event between ended at the gap and kept across it
- [ ] Truncated starts and ends are marked on the bands and named in the text alternative
- [ ] JUnit pins one hysteresis change and one `maxGap` change per page, and the Node parity test covers them
