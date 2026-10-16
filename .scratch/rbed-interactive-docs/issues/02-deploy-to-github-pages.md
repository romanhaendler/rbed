# 02: Deploy to GitHub Pages

**What to build:** Every push to main (and a manual dispatch) builds the library, the Scenario module and the web app and deploys the site to GitHub Pages, following umriss-ui's Pages workflow. The JVM and Node parity tests run before deployment and block it on failure. The README links to the site. From here on, every later ticket is visible live once merged. See spec: Implementation Decisions "Deployment"; Testing Decisions "One seam, run on two runtimes".

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] A Pages workflow builds and deploys on push to main and on manual dispatch
- [ ] The site is built from the rbed sources of the same commit, not from the last release
- [ ] A failing JVM or Node parity test stops the deployment
- [ ] The deployed site serves the page from 01 at the repository's Pages URL
- [ ] The README links to the site near the top
- [ ] The existing CI and Publish workflows are unaffected
