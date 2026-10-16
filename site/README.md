# rbed documentation site

The web app (React, Vite, TypeScript) is in this directory; `scenarios/` is the Scenario module, a Maven project that TeaVM compiles together with rbed to `scenarios/target/js/rbed.js`. See [ADR-0005](../docs/adr/0005-site-runs-rbed-through-teavm.md).

Needs Java 17+, Maven and Node 22+. From a clean checkout, in this directory:

```sh
npm ci
npm run build     # installs rbed from the repository root, compiles the Scenarios with TeaVM, builds dist/
npm test          # installs rbed, runs the JUnit tests and TeaVM, then the Node parity tests
npm run preview   # serves dist/ at http://localhost:4173/rbed/
```

`npm run dev` serves the app with hot reload once `npm run build:java` has produced the compiled module.
