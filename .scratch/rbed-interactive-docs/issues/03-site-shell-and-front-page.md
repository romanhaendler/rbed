# 03: Site shell and front page in umriss-ui style

**What to build:** The site looks and navigates like the umriss-ui Pages. Layout, controls and theming come from `@umriss-ui/core`, the time series from `@umriss-ui/charts`, both from npm at a pinned version (not umriss-ui's private demo shell). Navigation follows the README's chapters in the README's order; each page is a path of its own. The front page explains rbed in a few sentences, shows the Maven and Gradle coordinates with the rbed version of the build, lists the domain examples, and shows the first Rule from 01 with Channel lines and its Events as bands on the same time axis. See spec: Solution; Implementation Decisions "Web app", "Addresses".

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] Each page is reachable at its own path, and reloading any path shows that page
- [ ] The navigation lists the README's chapters and the domain examples in the README's order; pages not built yet are absent, not broken links
- [ ] Light and dark themes follow the system setting
- [ ] Every page works at phone width without horizontal scrolling
- [ ] The front page shows install coordinates with the rbed version from the build
- [ ] The first Rule is drawn with `@umriss-ui/charts`: Channel values as a line, each Event as a band, time in the Scenario's unit rather than milliseconds
- [ ] If the charts package lacks what bands need, the gap is noted in this ticket's Comments as a finding for umriss-ui; no second chart library is added
