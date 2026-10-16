# 03: Site shell and front page in umriss-ui style

**What to build:** The site looks and navigates like the umriss-ui Pages. Layout, controls and theming come from `@umriss-ui/core`, the time series from `@umriss-ui/charts`, both from npm at a pinned version (not umriss-ui's private demo shell). Navigation follows the README's chapters in the README's order; each page is a path of its own. The front page explains rbed in a few sentences, shows the Maven and Gradle coordinates with the rbed version of the build, lists the domain examples, and shows the first Rule from 01 with Channel lines and its Events as bands on the same time axis. See spec: Solution; Implementation Decisions "Web app", "Addresses".

**Blocked by:** 01

**Status:** done

- [x] Each page is reachable at its own path, and reloading any path shows that page
- [x] The navigation lists the README's chapters and the domain examples in the README's order; pages not built yet are absent, not broken links
- [x] Light and dark themes follow the system setting
- [x] Every page works at phone width without horizontal scrolling
- [x] The front page shows install coordinates with the rbed version from the build
- [x] The first Rule is drawn with `@umriss-ui/charts`: Channel values as a line, each Event as a band, time in the Scenario's unit rather than milliseconds
- [x] If the charts package lacks what bands need, the gap is noted in this ticket's Comments as a finding for umriss-ui; no second chart library is added

## Comments

**Done.** Checked in headless Chromium at 1280 px and 375 px, light and dark: no horizontal scroll, no page errors. The slider updates chart, table and source. Routing was checked with a temporary second page served by a static server below `/rbed/` (no SPA fallback): a click moves without a reload, back works, and reloading the path (with or without the trailing slash) shows the page. The drawer opens on a phone, closes on a move and puts the focus on the h1.

**Layout (site/src):**
- `pages.ts` is the page manifest (`path`, `title`, `description`, `rubric` "Guide" or "Examples"), in README order, importing nothing so Node can read it (`test/pages.test.mjs` imports it directly; Node 22.18+ strips the types). It has only the front page so far. A new page: add an entry at its README position and a view in `VIEWS` in `Shell.tsx`. The sidebar shows only rubrics that have pages.
- `Shell.tsx`: header (wordmark with version, GitHub and Maven Central links), sidebar from the manifest, `<dialog>` drawer at 900 px and less, a "No page at this address" view, `document.title`, and routing. Links are plain `<a href>`; a document click handler moves to any manifest page below `import.meta.env.BASE_URL` with `pushState` (keeping query and hash), `popstate` follows back and forward. It works with any base.
- `vite.config.ts`: reads the rbed version from the root pom into `__RBED_VERSION__` (declared in `rbed-scenarios.d.ts`), and a small build plugin copies `dist/index.html` to `dist/<path>/index.html` for every manifest page and to `dist/404.html` (GitHub Pages shows the app's "no page" view for unknown paths). Ticket 14's prerender replaces these copies. Ticket 02 sets `base: '/rbed/'` here. It also mutes the "use client" warnings of the umriss bundles.
- `ScenarioView.tsx` is the front page's live Scenario, meant for ticket 04 to generalise: the umriss configurator card (chart stage, a `Slider` per parameter in the panel, the Java source under the stage), then the Event table (`#events`, from 01, now in the Scenario's unit). `EventChart` draws one `Line` per Channel (markers on, so the Samples show) and one `StateBand` per Rule on a second y axis `lanes` (one unit per lane, first Rule on top, the Channels in the top three units; the course axis gets explicit ticks over the values and a domain stretched below them). Band rows: each Event is `{t: start, s: 0}`, `{t: end, s: null}`. The chart has `key={scenario.id}` because umriss compares `tickFormat` by source text. Times are `timestamp / timeUnit.millis`; the x axis label says the unit.
- `CodeBlock.tsx` (`CodeBlock` plus `CopyButton`, from umriss) and `site.css` (trimmed from umriss shell.css, page.css and front-page.html, tokens only, `color-scheme: light dark` follows the system). No syntax highlighting (umriss uses sugar-high, which targets JS); no theme toggle (the spec asks only to follow the system).
- `FrontPage.tsx`: hero, Maven and Gradle coordinates with the build's version, the first Rule live, the three steps, the domain examples as tiles (linked to their tests on GitHub until their pages exist; ticket 12/13 should point them at the pages).

**Bridge:** the catalogue now has `rules` (Rule names, from the Rules at default parameters), so a Rule keeps its lane when it has no Event. The first Scenario is shown in seconds (`timeUnit {name: "s", millis: 1000}`); timestamps in `run` stay milliseconds.

**Dependencies:** `@umriss-ui/core` and `@umriss-ui/charts` at exactly 0.40.0, `@fontsource/geist-sans` and `@fontsource/geist-mono` ^5.3.0. The bundle is now 470 KB JS (158 KB gzip) and 187 KB CSS (41 KB gzip), plus the Geist fonts.

- **umriss-ui gaps:** none blocking for bands. Event bands as `StateBand` lanes on a second numeric y axis work, but the app must stretch the Channel axis' domain itself to make room for the lanes and give it explicit ticks, otherwise ticks appear under the lanes (a "reserve the bottom N lanes" option on the chart would remove that arithmetic). A lane axis' `tickFormat` is compared by source text, so the lane names need a remount per Scenario.
