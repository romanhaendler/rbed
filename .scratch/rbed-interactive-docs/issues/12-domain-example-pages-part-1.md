# 12: Domain example pages, part 1

**What to build:** Pages for the cranes, manufacturing, HVAC, vital signs and fleet telematics examples. Each gets a parameterised Scenario whose default parameters reproduce its example test exactly, a two- or three-sentence explanation of the domain, links to the README section and to the example's test on GitHub, and links to the Javadoc of the methods it uses. The example tests stay unchanged. This ticket also adds the site's single browser smoke test. See spec: User Stories 26, 27, 41, 42, 53; Implementation Decisions "Scenarios mirror the existing examples"; Testing Decisions.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] Five pages, each with controls for its Rules' main parameters
- [ ] JUnit: each Scenario at default parameters yields exactly the Events its example test expects, in the same textual form
- [ ] The Node parity test covers each Scenario at its defaults
- [ ] Each page links to its README section, its example test and the Javadoc of the methods it uses
- [ ] The example tests are unchanged
- [ ] A Playwright smoke test opens one concept page and one domain page, moves a control and sees the Event table change; it runs in the Pages workflow
