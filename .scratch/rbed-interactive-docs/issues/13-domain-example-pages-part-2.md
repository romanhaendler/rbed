# 13: Domain example pages, part 2

**What to build:** Pages for the trading signals, cold chain, flood warning, interval training and deployment regression examples, following the pattern set in 12: a parameterised Scenario reproducing the example test at default parameters, a short domain explanation, and links to the README section, the example test and the Javadoc. See spec: User Stories 26, 27, 53.

**Blocked by:** 12

**Status:** done

- [x] Five pages, each with controls for its Rules' main parameters
- [x] JUnit: each Scenario at default parameters yields exactly the Events its example test expects
- [x] The Node parity test covers each Scenario at its defaults
- [x] Each page links to its README section, its example test and the Javadoc
- [x] The example tests are unchanged

## Comments

**Done.** Five pages (`/finance/`, `/logistics/`, `/environment/`, `/sports/`, `/it-operations/`, titled by the README's domain column, rubric "Examples", after Automotive). Checked in headless Chromium at 1280 and 375 px, light and dark: no horizontal scroll, no page errors; every slider moved to both ends and Reset on each page, and each control changes the Event table. The example tests and the rbed library are unchanged; the front page's last five tiles now link to the pages, so every tile is a page.

- **Scenarios** (`Finance`, `Logistics`, `Environment`, `Sports`, `ItOperations`, one line each in `Bridge.SCENARIOS` after `Automotive`): four or five parameters each (Finance two, the window lengths). Fractional values never reach a `long` in the source: Environment's rising rate is written `${rising} / (HOUR / 1000.0)` (the example's `10.0 / (HOUR / 1000)` would turn into integer division once the placeholder gives `10`), and its gap is a minute parameter (`${gapMinutes} * MINUTE`).
- **Sports** overrides `detect` with the example's listener, which also forwards `onEnded`/`onDiscarded` to the bridge's `Lifecycle` and keeps `lifecycle.now` current; the recovery times are kept per Recovered start in an instance field cleared on each `detect`, and `note` shows them (`51 s`). That relies on `run` being single-threaded (it is, in the browser and in JUnit). The source shows the example's listener as written.
- **ItOperations** declares `deploy` in `pulseChannels()`, so it is drawn as marks. It is separate from 09's `PulseChannels` Scenario (different data: one deployment, CPU, 120 minutes).
- **JUnit**: one test per Scenario at `{}` in `Samples.describe` form (the Finance golden-cross day 366 is the literal the example's brute force finds; Sports also asserts the notes), plus two pinning claims the pages make (a slow average of 250 starts the DeathCross at day 249; a 15-minute gap makes the missing 20 h report interrupt the FloodWarning, `5.25..19.75 truncatedEnd`).
- Intra-site links from page Markdown were avoided: a relative link would resolve wrongly in the twin at `dist/<name>.md`.
- **umriss-ui gaps:** none new.
