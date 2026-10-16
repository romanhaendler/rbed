# rbed documentation site

The web app (React, Vite, TypeScript) is in this directory; `scenarios/` is the Scenario module, a Maven project that TeaVM compiles together with rbed to `scenarios/target/js/rbed.js`. See [ADR-0005](../docs/adr/0005-site-runs-rbed-through-teavm.md).

Needs Java 17+, Maven and Node 22+. From a clean checkout, in this directory:

```sh
npm ci
npm run build     # installs rbed from the repository root, compiles the Scenarios with TeaVM, builds dist/
npm test          # installs rbed, runs the JUnit tests and TeaVM, then the Node parity tests
npm run preview   # serves dist/ at http://localhost:4173/rbed/
npm run test:browser  # after build: the Playwright smoke test against dist/ (once: npx playwright install chromium)
```

`npm run dev` serves the app with hot reload once `npm run build:java` has produced the compiled module.

## Adding a page

A page is three things; the build does the rest.

1. **A Scenario** (if the page shows one): a class in `scenarios/src/main/java/.../site/` extending `Scenario`, added to `Bridge.SCENARIOS`, with JUnit tests through the bridge in `BridgeTest` (the parity test repeats them against the compiled JavaScript). A Scenario that feeds its Detector otherwise than `detect(samples)` (a sampling rate, time advances) overrides `detect`; one whose listener makes something of an Event (a class, a distance) overrides `note`, which the Event table shows in a Note column. A domain example's Scenario reproduces its example test at the default parameters, asserted in `BridgeTest` in the test's own `Samples.describe` form.
2. **A manifest entry** in `src/pages.ts` at its README position: `path` (`/event-lifecycle/`), `title`, a one-sentence `description`, and `rubric` (`Guide` for a README chapter, `Examples` for a domain example).
3. **Its text** in `src/content/<name>.md`, named after the path (`/event-lifecycle/` reads `event-lifecycle.md`; the front page is `index.md`). It starts with `# Title`. The live Scenario stands where a line `<!-- scenario <id> -->` stands. `{{version}}` is the rbed version of the build.

The Markdown is a small subset (`src/markdown.ts`): `#` to `###` headings, paragraphs, `-` and `1.` lists (one level; a list of nothing but links is shown as tiles), fenced code with a language and an optional label (```` ```xml Maven ````), and inline `code`, `**strong**`, `*em*` and `[links](url)`. No raw HTML, no tables.

From these, `npm run build` (`prerender.mjs`, run at the end of `vite build`) writes for every page in the manifest:

- `dist/<path>/index.html`: the app with the page's head (title, description, canonical, Open Graph, the twin link) and its text prerendered, each Scenario as its Java source and Event table at the default parameters; the app replaces it when it starts;
- `dist/<name>.md`: the Markdown twin, the same text with each Scenario written out the same way;
- `llms.txt`, `llms-full.txt` (README, glossary, every twin in navigation order) and `sitemap.xml`.

The app reads the same Markdown for the page itself and for the search (Ctrl/Cmd+K), which finds a page by its title, its headings, its inline code and the methods its Java source calls. `public/og-image.png` is the sharing image of every page.
