# 12: Domain example pages, part 1

**What to build:** Pages for the cranes, manufacturing, HVAC, vital signs and fleet telematics examples. Each gets a parameterised Scenario whose default parameters reproduce its example test exactly, a two- or three-sentence explanation of the domain, links to the README section and to the example's test on GitHub, and links to the Javadoc of the methods it uses. The example tests stay unchanged. This ticket also adds the site's single browser smoke test. See spec: User Stories 26, 27, 41, 42, 53; Implementation Decisions "Scenarios mirror the existing examples"; Testing Decisions.

**Blocked by:** 04

**Status:** done

- [x] Five pages, each with controls for its Rules' main parameters
- [x] JUnit: each Scenario at default parameters yields exactly the Events its example test expects, in the same textual form
- [x] The Node parity test covers each Scenario at its defaults
- [x] Each page links to its README section, its example test and the Javadoc of the methods it uses
- [x] The example tests are unchanged
- [x] A Playwright smoke test opens one concept page and one domain page, moves a control and sees the Event table change; it runs in the Pages workflow

## Comments

**Done.** Five pages (`/cranes/`, `/manufacturing/`, `/building-services/`, `/healthcare/`, `/automotive/`, titled by the README's domain column, rubric "Examples", after every Guide page). Checked in headless Chromium at 1280 and 375 px, light and dark: no horizontal scroll, no page errors; sliders with fractional steps (0.05, 0.25, 0.5) give clean values. The example tests and the rbed library are unchanged.

- **Scenario contract** (additive, in `Scenario.java`): `detect(Rule[])` defaults to `Detector.builder().rules(rules).build().detect(samples())`; Manufacturing overrides it for `samplingRate(1)` + `detectValues`, Healthcare for the push/`advanceTo` loop with an `onEnded` listener (its `samples()` are the pushed ones only, so the chart shows the dropout). `note(Event, Map<String, Double> values)` (default null) is the listener-side classification: Cranes "pallet"/"container", Building services "short cycle", Automotive "11.94 km" (formatted by hand from a long, so TeaVM and the JVM agree). `Bridge.run` passes the values on to `result`, which writes `"note"` only when non-null.
- **Event table and prerender**: a Note column appears only when some Event has a note (`ScenarioView` `EventTable`, `prerender.mjs` `eventRows`, tested in `test/prerender.test.mjs`). Ticket 09's Aggregates columns go next to it.
- **JUnit**: `BridgeTest.describe(result, unit)` mirrors `Samples.describe` ("Name start..end" in the unit, plus Truncated flags), `notes(result)` lists the notes. One test per domain Scenario at `{}`; the crane test appends the note as `CraneScenarioTest` does. All go through `run(...)`, so the parity test covers them.
- **Parameters**: the main thresholds and durations of each Rule (four or five per page); fixed numbers that are not parameters stay literal in the source. Durations in the source keep the example's style (`${x} * SECOND`, `Duration.ofSeconds(${x})`), so steps are whole seconds or minutes.
- **Pages**: domain text from the example's Javadoc and comments only; links to the test on GitHub, the README's `#examples` section and the Javadoc on javadoc.io (`latest`) with method anchors. The Markdown link syntax allows no parentheses in URLs, so anchors are percent-encoded (`#forDuration%28long%29`), which browsers resolve. The front page's first five tiles link to the pages relatively (`cranes/`); the other five still go to GitHub (ticket 13).
- **Playwright**: `@playwright/test` 1.63.0 (exact) in `site/`, `playwright.config.mjs` (Chromium only, starts `npm run preview`), `e2e/smoke.spec.mjs`: on the front page (the only concept page on this branch; a later ticket may swap in a Guide page) and on `/cranes/` it presses End on a slider and waits for `#events tbody` to change, with no page errors. `npm run test:browser` runs it after `npm run build`; the Pages workflow runs `npx playwright install --with-deps chromium` and `npm run test:browser` after the build, before the upload. `test-results/` and `playwright-report/` are git-ignored.
- **umriss-ui gaps:** none new. With 1500 Samples (Automotive) the markers of `Line markers="always"` merge into a thick line; a "markers only when sparse" option would help. Channels of very different ranges share one axis (speed 0..20 m/s under rpm 0..800), so the smaller one looks flat until the other is hidden; that is the site's single course axis, not an umriss gap.

- **Merged with 09:** the result's Events carry `aggregates` and then `note`; the Note column stands before 09's Aggregates column in the prerendered table, and 09's Aggregates sub-row spans 6 columns when there is a Note column. On a phone the Note column (like Truncated) is reached by scrolling the table sideways; the page itself does not scroll. The smoke test now uses the concept page `/aggregates/` ("Driving above") instead of the front page. Checks re-run after the merge: `mvn -q verify`, `npm ci`, `npm run build`, `npm test` (57 Node tests), `npm run test:browser`, and the 1280/375 px light/dark check.
