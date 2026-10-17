# 04: Site reads its plotted Expressions from rbed

**What to build:** The documentation site reads every plotted Expression through `watch`/`value` instead of the bisection workaround. The Scenario module watches each plotted Expression and reads it after every push and every time advance in the shared feeding helpers, so a plot costs one run and also has values at advance moments (the silent sensors page shows `hr.age()` growing during the silence). The bisection code and its bisection-only tests are removed. The Conditions and Expressions page gains a short "Read an Expression's value" passage with a Java snippet that SourceTest compiles and runs. ADR-0005's note on reading Expression values and the site README are updated. See spec: Solution 3 (last sentence); Implementation Decisions "Documentation site"; Testing Decisions "Site tests".

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] Plotted Expressions are read through `watch`/`value`; the bisection code is gone
- [ ] BridgeTest's existing assertions on plotted values pass unchanged at every Sample (exact equality with the former values)
- [ ] Plotted values now include advance moments; new BridgeTest assertions pin them on the silent sensors Scenario
- [ ] The silent sensors chart shows `hr.age()` growing across the silence, and its text alternative and readout agree with the line
- [ ] The Conditions and Expressions page has a "Read an Expression's value" passage with a snippet that SourceTest compiles and runs, and Javadoc links to the new methods that resolve against the generated Javadoc
- [ ] ADR-0005 and site/README.md describe the new way of reading values; no mention of the bisection is left
- [ ] Run time of a Scenario with a plotted Expression drops measurably (record before/after in this ticket's Comments)
- [ ] `mvn -q verify` at the root; in site/: `npm ci`, `npm run build`, `npm test`, `CI=1 PORT=<free port> npm run test:browser` pass; every page at 1280/375, light/dark, has no page errors and no axe violations
