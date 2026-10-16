# The documentation site runs rbed in the browser through TeaVM; the library takes no site code

The documentation site (`site/`) shows Events detected live while the reader changes a Rule's parameters. To show the results of the very library that is released, rbed and a small Scenario module (`site/scenarios`, its own Maven build) are compiled together from bytecode to one JavaScript module by TeaVM (JavaScript target). The web app calls that module's two functions, `scenarios()` and `run(scenarioId, parameters, drawnChannels)`, with JSON text in and out. The library itself stays as it is: no TeaVM dependency or annotation, no code written for the site; everything site-specific lives in the Scenario module. TeaVM 0.16.0 compiles rbed unchanged, and the compiled module is about 110 KB (41 KB gzipped).

## Alternatives considered

- **TeaVM's WebAssembly target.** Rejected for now: the JavaScript target is more mature and its interop with plain strings is simpler.
- **CheerpJ, a full JVM in the browser.** Rejected: a large download, a slow start and licensing questions, for compiling arbitrary Java in the browser, which the site does not need.
- **A JavaScript port of rbed.** Rejected: a second implementation that can drift from the released one.
- **Results precomputed on the JVM at build time.** Kept as the fallback if TeaVM stops compiling rbed: free parameter values and drawn Channels would be lost.

## Consequences

- The Scenario module builds against the rbed of the same commit (`mvn install` from the repository root first; `site/package.json` does both).
- Its tests run every bridge call on the JVM and repeat it against the compiled JavaScript in Node, so a difference between the two runtimes fails the build.
- Code in the Scenario module avoids `String.format`, `String.split` and regular expressions, which pull large parts of TeaVM's class library into the module, and `long` in the exported signatures, which becomes a BigInt in JavaScript.
- A future library change that TeaVM cannot compile shows up as a failing site build, not in the library's own build.
