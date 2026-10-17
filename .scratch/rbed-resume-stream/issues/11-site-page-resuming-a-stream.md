# 11: Site page "Resuming a stream"

**What to build:** A Guide page "Resuming a stream" after "Restarts: History" on the documentation site. Its Scenario lets the reader move a stop point and choose the restart (naive, History only, Replay Point, Snapshot); the chart marks the stop, the Replay Point and the Memory Horizon, and shows which Events differ from the uninterrupted run. The bridge result carries the Replay Point and Memory Horizon at the stop and the uninterrupted run's Events. A Snapshot written on the JVM restores in the compiled browser module and vice versa, and both continue identically. Built with the site's recipe ("Adding a page"), so prerendering, the Markdown twin and the llms files follow. See spec: Implementation Decisions "Documentation site"; Testing Decisions "Across runtimes", "Site".

**Blocked by:** 10

**Status:** ready-for-agent

- [ ] The page exists in the Guide after "Restarts: History", with prose adapted from the README section and links to its Javadoc members
- [ ] At the defaults, the Replay Point and Snapshot restarts equal the uninterrupted run and the naive and History-only restarts do not (BridgeTest), and the page says so verifiably
- [ ] The chart marks the stop, the Replay Point and the Memory Horizon, and differing Events are visible and named in the text alternative
- [ ] Parity: Snapshots cross between the JVM and the compiled module in both directions and continue identically
- [ ] SourceTest compiles and runs the page's Java; the twin and llms-full.txt read correctly without the live parts
- [ ] All site checks pass (`npm test`, `test:browser`, every page at 1280/375 light/dark without page errors or axe violations)
