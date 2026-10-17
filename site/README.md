# rbed documentation site

The web app (React, Vite, TypeScript) is in this directory; `scenarios/` is the Scenario module, a Maven project that TeaVM compiles together with rbed to `scenarios/target/js/rbed.js`. See [ADR-0005](../docs/adr/0005-site-runs-rbed-through-teavm.md).

Needs Java 17+, Maven and Node 22.18+ (it runs the tests' TypeScript imports without a flag). From a clean checkout, in this directory:

```sh
npm ci
npm run build     # installs rbed from the repository root and builds its Javadoc, compiles the Scenarios with TeaVM, builds dist/
npm test          # installs rbed (and its Javadoc), runs the JUnit tests and TeaVM, then the Node tests
npm run build:web # builds dist/ from the module npm test compiled, as the Pages workflow does
npm run preview   # serves dist/ at http://localhost:4173/rbed/
npm run test:browser  # after build: the Playwright smoke tests against dist/, every page at 1280 and 375 px (once: npx playwright install chromium)
```

`npm run dev` serves the app with hot reload once `npm run build:java` has produced the compiled module.

## Where things are

- `src/bridge.ts`: the compiled module's two functions (`scenarios`, `run`), typed.
- `src/ScenarioView.tsx`: one Scenario: chart and panel, tables, source.
- `src/EventChart.tsx`: draws what `src/chartModel.ts` computes, with the umriss charts.
- `src/chartModel.ts`: everything the chart computes (bands, axes, lanes, thresholds, windows, the readout), pure, tested in `test/chartModel.test.mjs`.
- `src/plotArea.ts`: the only dependency on @umriss-ui/charts internals, which is why the package is pinned.
- `src/events.ts`, `src/aggregates.ts`: the Event tables' text, shared by the app and `prerender.mjs`.
- `src/markdown.ts`: the pages' Markdown subset; `src/pages.ts`: the page list; `src/search.ts`: the search.
- `prerender.mjs`: the prerendered pages, the twins, llms.txt and the rest (see below).
- `test/`: the Node tests (`npm test`); `e2e/`: the Playwright tests (`npm run test:browser`).

## Adding a page

A page is three things; the build does the rest.

1. **A Scenario** (if the page shows one), a class in `scenarios/src/main/java/.../site/` extending `Scenario`.
   - **Required.**
     - The class is named after its page (`Cranes`, `Count`; with the suffix `Scenario` only where that name is a public rbed type: `AfterScenario`, `AggregatesScenario`) and added to `Bridge.SCENARIOS`.
     - `samples(values)` gives its Samples (`values`: the parameter values by id, which most Scenarios ignore).
     - `rules(values)` gives its Rules. Their names must not depend on the parameters: `Bridge.scenarios()` reads them once, at the defaults, for the lanes and the toggles.
     - Its parameters: a number with a slider, or a choice (`Parameter.choice`, shown as radio buttons) whose value is the index of an option; its `${id}` in the source stands for that option's source, and a number only unchosen options name gets no control.
     - `source` is what a reader copies, so it is complete: its imports (with `import static io.github.romanhaendler.rbed.Conditions.*;` where it uses one), then statements that run as the body of a method, declaring everything they use, feeding the Scenario's own data and closing the Detector, then any methods they call under a line `// In the enclosing class:`. Time values are `java.time.Duration`.
     - JUnit tests through the bridge in `BridgeTest` (the parity test repeats them against the compiled JavaScript). A domain example's Scenario reproduces its example test at the default parameters, asserted in `BridgeTest` in the test's own `Samples.describe` form. `SourceTest` compiles every source and runs it at its defaults: its Detectors must report exactly the Events the bridge reports, and every threshold and window the chart draws must be one the source compares with or opens.
   - **Overrides**, each optional:
     - `detect(rules, samples, values, lifecycle)`: for a Scenario that feeds its Detector otherwise than pushing each Sample in turn (a sampling rate, time advances, a restart). It feeds through `lifecycle` (`push`, `pushValues`, `advance`, and `restart(at, withHistory)` for a new Detector), so the bridge sees every moment.
     - `note(event, values)`: what its listener makes of an Event (a class, a distance), shown in the Event table's Note column. A listener that needs more than the Event (the end of an earlier one) calls `lifecycle.note(event, text)` in `detect` instead, so a Scenario keeps no state of its own.
     - `plots(values)`: Expressions to draw as dashed lines, from values rbed computes (`ExpressionValues`); a `Plot` with a threshold names its Rule and comparison as a `Threshold` does, and one with a `unit` of its own gets its own axis.
     - `thresholds(values)`: where its Rules compare a Channel with a value, one `Threshold` per Rule part and comparison (`"when >"`, `"endWhen <"`).
     - `windows(values)`: the time each `after(...).within(...)` counts (`Window(rule, after, millis)`).
     - `pulseChannels()`: its Pulse Channels; rbed does not tell a Channel's kind, and without it a Pulse Channel is drawn as a held line.
     - `readsLatest()`: the Rule whose latest Event its Rules read, which gives the readout its `latest(...)` line and the chart its highlight.
   - **What the chart does with them.**
     - Channels of very different scales (more than four times apart) get bands and axes of their own: the first band's axis on the left, every further one, and that of Expressions with a unit of their own, on the right.
     - Thresholds of one value share a line, labelled with the comparison alone; the key names each in full. A window is a strip over the lane of the Rule that counts it, from the end of every Event of `after`; a restart without History cuts it there.
     - One rule for lines on every page: a Channel's line joins its consecutive Samples; where rbed holds a value instead (a Sample lacks the Channel, or time was advanced without a Sample, where every Channel holds its value), the line breaks, a thin step line carries the held value, and the hover readout marks it "(kept)".
     - A Pulse Channel holds nothing: it is drawn as marks, and the readout shows "–" wherever a Sample does not carry it.
     - The bridge reports Events in rbed's order (as they Ended); the site shows them in the order they started (`forDisplay` in `src/events.ts`).
2. **A manifest entry** in `src/pages.ts` at its README position: `path` (`/event-lifecycle/`), `title`, a one-sentence `description`, and `rubric` (`Guide` for a README chapter, `Examples` for a domain example).
3. **Its text** in `src/content/<name>.md`, named after the path (`/event-lifecycle/` reads `event-lifecycle.md`; the front page is `index.md`). It starts with `# Title`. The live Scenario stands where a line `<!-- scenario <id> -->` stands; `<!-- scenario <id> draw -->` also lets the reader draw the values of its Channels (not its Pulse Channels), which suits concept pages; its Scenario must then give the same number of Samples for every parameter value, since the drawn values are kept when a parameter moves (`test/drawable.test.mjs`). A trailing `hide <names>` (comma-separated) starts the chart with those Channels or Rules hidden (`<!-- scenario logistics hide door -->`); a band whose Channels are all hidden gives up its axis, which on a phone keeps the plot wide. Fenced blocks right after one another (Maven, Gradle) are shown as tabs of one. `{{version}}` is the rbed version of the build.

The Markdown is a small subset (`src/markdown.ts`): `#` to `###` headings, paragraphs, `-` and `1.` lists (one level; a list of nothing but links is shown as tiles), fenced code with a language and an optional label (```` ```xml Maven ````), and inline `code`, `**strong**`, `*em*` and `[links](url)`; a link to another page is relative to the page (`../cranes/`), and the twin makes it absolute. A link into the Javadoc is relative too (`../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29`; `api/…` on the front page), and the build fails if the generated Javadoc has no such file or member. A line `<!-- live -->` before a paragraph or list marks it as speaking of the live page's controls ("Move the threshold…"): the prerendered page and the twin leave it out. No raw HTML, no tables.

From these, `npm run build` (`prerender.mjs`, run at the end of `vite build`) writes for every page in the manifest:

- `dist/<path>/index.html`: the app with the page's head (title, description, canonical, Open Graph, the twin link) and its text prerendered in the site's header and navigation, each Scenario as its Event table and Java source at the default parameters; the app replaces it when it starts, and a reader without JavaScript reads it;
- `dist/<name>.md`: the Markdown twin, the same text with each Scenario written out the same way, its parameters listed first;
- `llms.txt`, `llms-full.txt` (README, glossary, every twin in navigation order), `sitemap.xml` and `404.html`;
- `dist/api/`: rbed's Javadoc of the same commit, which `npm run install:rbed` builds (`mvn -Prelease javadoc:javadoc`, with a link back to the site as its `-header` and the MIT licence as its `-bottom`), in the site's fonts and colors (`api.css`, which sets the custom properties of JDK 21's Javadoc stylesheet; the Pages workflow uses JDK 21).

`node version.mjs` prints the rbed version of the root pom, which the scripts pass to the Scenario module (`-Drbed.version`; it has no default). `PORT=4391 npm run test:browser` serves the site on another port than 4173.

The app reads the same Markdown for the page itself and for the search (Ctrl/Cmd+K), which finds a page by its title, its text, its inline code and the methods its Java source calls, and a section by its heading. `public/og-image.png` is the sharing image of every page.
