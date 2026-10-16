# 11: Draw a Channel's values

**What to build:** On concept pages, the reader can draw a Channel's values on the chart with a mouse or a finger and watch Events appear as they draw; a reset restores the Scenario's own values. The bridge's `drawnChannels` argument replaces the generated values of those Channels. Drawn values are not put in the query string. See spec: User Stories 24 and 25; Implementation Decisions "The bridge".

**Blocked by:** 05

**Status:** ready-for-agent

- [ ] Drawing with a mouse and with touch changes the Channel's values and the Events update while drawing
- [ ] Reset restores the Scenario's own values
- [ ] `run` uses drawn values in place of generated ones for those Channels only
- [ ] Drawing does not scroll the page on a phone
- [ ] JUnit and the Node parity test cover one Scenario with a drawn Channel
