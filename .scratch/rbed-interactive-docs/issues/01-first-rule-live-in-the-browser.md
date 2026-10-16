# 01: First Rule live in the browser (TeaVM tracer bullet)

**What to build:** The README's first Rule (`HighTemperature`, `temp.above(80)`) runs in a browser, compiled from Java with TeaVM, and a plain web page shows its Channel values, its Events and a slider for the threshold that re-runs the Detector on every change. This is the feasibility spike and the first tracer bullet through every layer: the Scenario module with the first Scenario, the bridge (`scenarios()` and `run(scenarioId, parameters, drawnChannels)`, JSON text across the boundary, written by a small hand-written writer), the TeaVM build, and the web app (React, Vite) that loads the compiled module. The page may be unstyled; the site shell comes in 03. See spec: Implementation Decisions "TeaVM, JavaScript target", "First step is a feasibility spike", "A Scenario", "The bridge", "The result of run".

If TeaVM cannot compile rbed unchanged, stop: record what failed and the fallback options in this ticket's Comments, set it to needs-info, and do not change the library.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] The library module and its jar are unchanged: no new dependency, no TeaVM annotations
- [ ] The Scenario module builds against the rbed artifact of the same commit
- [ ] `scenarios()` returns the first Scenario with its Channels, its threshold parameter (default, limits, step, unit) and its source template
- [ ] `run` returns the Samples and each Event with name, start, end and Truncated flags; invalid parameters return rbed's error message instead of a result
- [ ] JUnit on the JVM: the first Scenario at default parameters yields the README's Event (`HighTemperature` from 2000 to 3000), and a changed threshold yields the expected different Events
- [ ] Node test against the compiled JavaScript: for the same inputs, results equal the JVM's, comparing numbers as parsed values
- [ ] The web page shows the Channel values, the Events, and a threshold slider; moving it updates the Events without a page reload
- [ ] The size of the compiled module (raw and gzipped) is recorded in this ticket's Comments
- [ ] ADR-0005 records that the documentation site runs rbed in the browser through TeaVM and that the library takes no site-specific code
