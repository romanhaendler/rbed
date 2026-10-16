# 02: Deploy to GitHub Pages

**What to build:** Every push to main (and a manual dispatch) builds the library, the Scenario module and the web app and deploys the site to GitHub Pages, following umriss-ui's Pages workflow. The JVM and Node parity tests run before deployment and block it on failure. The README links to the site. From here on, every later ticket is visible live once merged. See spec: Implementation Decisions "Deployment"; Testing Decisions "One seam, run on two runtimes".

**Blocked by:** 01

**Status:** done

- [x] A Pages workflow builds and deploys on push to main and on manual dispatch
- [x] The site is built from the rbed sources of the same commit, not from the last release
- [x] A failing JVM or Node parity test stops the deployment
- [ ] The deployed site serves the page from 01 at the repository's Pages URL
- [x] The README links to the site near the top
- [x] The existing CI and Publish workflows are unaffected

## Comments

**Done, except the live deployment.** "The deployed site serves the page from 01" is not ticked: it needs GitHub Pages enabled for the repository (Settings → Pages → Source: GitHub Actions) and a push to main, neither of which could happen here. The built `dist/` was served by `npm run preview` at `/rbed/` and checked in headless Chromium (1280 and 375 px, light and dark): the page loads, no failed requests, no page errors, `HighTemperature` shown.

- `.github/workflows/pages.yml` follows umriss-ui's: one `build` job (Java 17 Temurin with Maven cache, Node 22 with npm cache keyed on `site/package-lock.json`, then in `site/`: `npm ci`, `npm test`, `npm run build`, upload `site/dist`) and a `deploy` job that needs it (`actions/deploy-pages@v5`, environment `github-pages`). `npm test` runs the JUnit tests and the Node parity test, so a failure there fails `build` and skips `deploy`. Both npm scripts install rbed from the repository root first, so the site is always built from the rbed of the same commit. Concurrency group `pages`, not cancelled in progress. Validated with actionlint 1.7.12 (clean); `ci.yml` and `publish.yml` are untouched (actionlint reports three pre-existing SC2086 infos in `publish.yml`).
- `site/vite.config.ts` sets `base: '/rbed/'`. Asset URLs in `dist/` start with `/rbed/`; `npm run preview` and `npm run dev` serve the app at `http://localhost:<port>/rbed/`, not at `/`. Links between pages added later must respect the base (`import.meta.env.BASE_URL`).
- The README links to the site right below the badges.
