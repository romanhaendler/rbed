# 08: Referring to other Rules pages

**What to build:** Concept pages for Rules that refer to other Rules: `after(...).within(...)` with a control for the window, showing which Events of the first Rule still count for the second; `during(...)`, showing one Rule's Events gating another's; `count(...).within(...)` and `totalDuration(...)`, plotting the running count or duration against its limit; and `latest(...)`, showing which Event a Rule reads. Each page draws both Rules' bands on one time axis. See spec: User Stories 15 to 18.

**Blocked by:** 05

**Status:** ready-for-agent

- [ ] The `after` page: moving the window changes which second-Rule Events occur
- [ ] The `during` page: the gating Rule's bands and the gated Rule's bands line up visibly
- [ ] The `count`/`totalDuration` page: the running count or duration is plotted with its limit
- [ ] The `latest` page: the Event that `latest` reads at a hovered timestamp is highlighted
- [ ] JUnit pins one parameter change per page, and the Node parity test covers them
