# Spec: interactive documentation site

Status: ready-for-agent

## Problem Statement

rbed's documentation is a single 40 KB README plus ten example tests. Everything the library is about happens over time: an Event goes Pending, gets Confirmed or Discarded, Ends, and is Truncated at the edges of the data. A reader has to replay that timing in their head from code blocks and lines like `"Run 100..700"`. Questions such as "what happens if the duration is 40 s instead of 30 s?" or "why does QuickRestart end before Run?" can only be answered by cloning the repo, editing a test and running Maven.

The sibling project umriss-ui shows the documentation the author wants: a site on GitHub Pages with a page per topic, live examples with their source, controls that change parameters and redraw at once, prerendered pages, Markdown twins and an `llms.txt`. rbed has nothing comparable. Since rbed is a Java library, it cannot simply be imported into a web page the way a TypeScript library can.

## Solution

A documentation site for rbed on GitHub Pages, styled like the umriss-ui Pages, on which the real rbed runs in the browser.

- rbed and a small catalogue of parameterised **Scenarios** are compiled from Java bytecode to JavaScript with TeaVM. No Java is installed on the reader's side, no server is involved, and the results are those of the very same library code that is released to Maven Central.
- Every page shows Channel values over time as a chart, the Events of each Rule as bands on the same time axis with their lifecycle marked (Pending start, Confirmed, Ended, Discarded), and a table of the Events with start, end, Truncated flags and Aggregates.
- Controls (sliders, toggles, number inputs) change the parameters of a Scenario's Rules (threshold, duration, End Condition, `within` window, moving-average size, ...). The Detector runs again on every change and the chart, bands and table update immediately.
- Next to the chart, the Java source of the Rules is shown with the current parameter values filled in, so what the reader sees is code they can copy.
- On concept pages, the reader can also draw a Channel's values with the mouse or finger and watch Events appear.
- The pages follow the README's chapters (lifecycle, End Conditions, data gaps, Conditions and Expressions, referring to other Rules, Aggregates, batch and sampling rates, silent sensors, Pulse Channels, History) plus one page per domain example (cranes, manufacturing, HVAC, vital signs, fleet telematics, trading, cold chain, flood warning, interval training, deployments).
- Like the umriss-ui Pages: prerendered pages with their text, a Markdown twin per page, `llms.txt`, light and dark themes, phone-width layout. Built and deployed on every push to main.

## User Stories

1. As a developer evaluating rbed, I want to open a link and see an Event being detected on a chart, so that I understand what the library does within seconds without installing anything.
2. As a developer evaluating rbed, I want to see the Maven and Gradle coordinates with the current version on the front page, so that I can try it right away.
3. As a developer evaluating rbed, I want a list of domain examples with a one-line description each, so that I can jump to the one closest to my problem.
4. As a new user, I want the first Rule from the README running live on the front page, so that the three steps (build, push, close) are tied to a visible result.
5. As a new user, I want to change the threshold of the first Rule with a slider, so that I see the Event's start and end move with it.
6. As a new user, I want the Event lifecycle page to show Pending, Confirmed, Ended and Discarded as distinct marks on the time axis, so that I learn the four states visually.
7. As a new user, I want to raise a Rule's duration on the lifecycle page until a short Event turns from Confirmed into Discarded, so that I understand what `forDuration` does.
8. As a new user, I want to see the timestamp at which an Event becomes Confirmed separately from its start, so that I understand that the start lies before the confirmation.
9. As a user, I want an End Condition page where I can move the end threshold apart from the start threshold, so that I see hysteresis remove flapping Events.
10. As a user, I want to give the End Condition its own duration with a control, so that I see how it delays the end of an Event.
11. As a user, I want a data gap page where I can move a gap in the Samples and change `maxGap`, so that I see when an Event is ended or kept across the gap.
12. As a user, I want the Truncated flags of an Event shown on its band and in the table, so that I can tell Events cut by the edges of the data from complete ones.
13. As a user, I want a Conditions and Expressions page where I pick an Expression (rate, moving average over Samples, moving average over a duration, arithmetic between Channels) and see its value plotted as an extra line, so that I see what the Condition actually compares.
14. As a user, I want to see where an Expression is undefined (for example during a moving average's warm-up), so that I understand why no Event starts there.
15. As a user, I want a page on `after(...).within(...)` where I move the window and see which second Rule's Events still count, so that sequences become tangible.
16. As a user, I want a page on `during(...)`, so that I see that one Rule's Events gate another's.
17. As a user, I want a page on `count(...).within(...)` and `totalDuration(...)`, so that I see the running count or duration against the limit over time.
18. As a user, I want a page on `latest(...)`, so that I see how a Rule reads the most recent Event of another Rule.
19. As a user, I want an Aggregates page where I click an Event and see its per-Channel minimum, maximum and mean, so that I know what an Event carries.
20. As a user, I want the batch and sampling-rate page to let me switch between push-based and sampling-rate mode, so that I see the difference in how durations are measured.
21. As a user, I want a silent sensor page where I see `advanceTo` move time forward without Samples, so that I understand `age()` and how Events end while a sensor is silent.
22. As a user, I want a Pulse Channel page where the Pulse values are shown as marks rather than a line, so that I see that they have no value between Samples.
23. As a user, I want a History page where I toggle supplying History on a restart, so that I see a Rule depending on earlier Events behave differently with and without it.
24. As a user, I want to draw a Channel's values on concept pages with the mouse or a finger, so that I can test edge cases the prepared data does not cover.
25. As a user, I want to reset a drawn Channel to the prepared values, so that I can start over.
26. As a user, I want each domain example (cranes, manufacturing, HVAC, vital signs, fleet telematics, trading, cold chain, flood warning, interval training, deployments) as its own page, so that I see a complete realistic Rule set at work.
27. As a user, I want each domain example page to explain the domain in two or three sentences, so that I understand why these Rules matter there.
28. As a user, I want the Java source of the Rules shown next to the chart with the current parameter values filled in, so that I can copy working code.
29. As a user, I want a copy button on every code block, so that copying is one click.
30. As a user, I want a reset control that restores a Scenario's default parameters, so that I can return to the documented case.
31. As a user, I want the current parameter values reflected in the page address, so that I can share a link to exactly what I see.
32. As a user, I want to hover over the chart and read the Channel values and Rule states at that timestamp, so that I can inspect a specific moment.
33. As a user, I want to toggle the visibility of individual Rules and Channels, so that busy examples stay readable.
34. As a user, I want an Event table under the chart with name, start, Confirmed time, end, Truncated flags and Aggregates, so that I can read exact numbers.
35. As a user, I want clicking an Event in the table to highlight its band on the chart, and vice versa, so that table and chart stay connected.
36. As a user, I want time shown in the unit the Scenario uses (seconds, minutes, hours), so that I do not have to convert milliseconds.
37. As a user, I want invalid parameter combinations rejected with the message rbed itself gives (for example a Retention Limit violation in `build()`), so that I learn the library's validation too.
38. As a user, I want the result to update within a frame or two of moving a slider, so that exploring feels immediate.
39. As a user, I want a search across all pages, so that I can find where a method like `averageOverDuration` is explained.
40. As a user, I want navigation that follows the README's chapters, so that the site and the README tell the same story in the same order.
41. As a user, I want links from each page to the matching README section and to the example's test on GitHub, so that I can go to the source.
42. As a user, I want a link to the Javadoc of each method a page uses, so that I get the precise contract.
43. As a user on a phone, I want every page to work at phone width without horizontal scrolling, so that I can read it anywhere.
44. As a user, I want light and dark themes following my system setting, so that the site is comfortable to read.
45. As a user with a screen reader, I want every chart to have a text alternative listing the Events, so that the content is accessible without the visuals.
46. As a user navigating by keyboard, I want all controls reachable and operable by keyboard, so that I do not need a mouse.
47. As a user with JavaScript disabled or a slow connection, I want each page's text, code and default Event table to be present in the prerendered HTML, so that the page is useful before or without the live part.
48. As a coding agent, I want an `llms.txt` and a Markdown twin of every page, so that I can read the documentation without rendering it.
49. As a search engine, I want every page at its own path with its text in the HTML, a sitemap and a sharing image, so that pages can be indexed and shared.
50. As the maintainer, I want the site built and deployed by a GitHub Actions workflow on every push to main, so that it never lags behind the code.
51. As the maintainer, I want the site to use the rbed sources of the same commit, so that the site shows the behaviour of the code next to it, not of the last release.
52. As the maintainer, I want the site to show the rbed version from the build, so that readers know which version they are looking at.
53. As the maintainer, I want a test that fails if a Scenario at its default parameters stops producing the Events its domain example test expects, so that the site and the examples cannot drift apart.
54. As the maintainer, I want a test that fails if the compiled JavaScript produces different Events than the JVM for any Scenario, so that the browser never shows something the library would not do.
55. As the maintainer, I want the site to use `@umriss-ui/core` and `@umriss-ui/charts` from npm, so that it looks like the umriss-ui Pages and exercises my own chart library.
56. As the maintainer, I want the library itself to stay free of runtime dependencies and of any site-specific code, so that the site adds nothing to the released jar.
57. As the maintainer, I want adding a Scenario to mean writing one Java class with its Rules, parameters and data plus one page entry, so that the site grows with little effort.
58. As the maintainer, I want the README to link to the site, so that readers find the interactive version.

## Implementation Decisions

- **Two new parts, both outside the released library.** The library module stays exactly as it is: no new dependency, no TeaVM annotations, nothing in its jar changes. The site lives in its own directory in the repo and consists of:
  - a **Scenario module** (Java, own Maven build, depends on the rbed artifact of the same commit), and
  - a **web app** (TypeScript, React, Vite) that renders the pages.
- **TeaVM, JavaScript target.** The Scenario module and rbed are compiled together to one JavaScript module with TeaVM. The JavaScript target is chosen over TeaVM's WebAssembly target for its maturity and simpler interop. CheerpJ (a full JVM in the browser) was rejected: large download, slow start, and licensing questions, for a capability (compiling arbitrary Java in the browser) the site does not need. A JavaScript port of rbed was rejected because it would be a second implementation that can drift.
- **First step is a feasibility spike.** rbed's main code uses only `java.util`, `java.util.function` and `java.time.Duration`, with no reflection, threads or `String.format`, which suggests TeaVM compiles it unchanged. That is not yet verified. The first ticket compiles rbed plus one Scenario with TeaVM, runs it in a browser and in Node, and records the bundle size. If TeaVM cannot compile rbed unchanged, the fallback is the precomputed variant (see Further Notes), not a change to the library.
- **A Scenario** is one Java class that declares:
  - an id, a title and the time unit it is shown in,
  - its Channels, and for each whether it is a Pulse Channel,
  - its parameters: id, label, kind (number or boolean), default, minimum, maximum, step and unit,
  - how to produce its Samples (generated by a function of time, like the example tests do, or taken from Channel values the reader drew),
  - how to build its Rules from parameter values,
  - a Java source template of those Rules in which parameter placeholders are replaced by the current values, for display.
- **The bridge is the only seam between Java and the web app.** The Scenario module exports exactly two functions to JavaScript:
  - `scenarios()` returns the catalogue: for each Scenario its id, title, time unit, Channels, parameters with defaults and limits, and source template.
  - `run(scenarioId, parameters, drawnChannels)` builds a Detector from the Rules for these parameters, feeds it the Samples (the Scenario's own, with any drawn Channel values replacing the generated ones), and returns the result.
  - Data crosses the bridge as JSON text, to keep TeaVM interop to plain strings. The JSON is written by a small hand-written writer in the Scenario module (no JSON library, matching the project's zero-dependency habit).
- **The result of `run`** contains:
  - the Samples (timestamp and Channel values; absent values of Pulse Channels stay absent),
  - for each Rule, every Event with name, start, Confirmed timestamp, end, Truncated start/end flags and Aggregates,
  - every Discarded Pending Event with its start and the timestamp at which it was Discarded,
  - optionally, for Scenarios that ask for it, the values of an Expression at every Sample (undefined where it is undefined), for plotting,
  - or, instead of all of the above, the message of the exception rbed threw while building or running (invalid parameters are shown, not hidden).
  Lifecycle timestamps come from an EventListener the bridge registers. It records the same callbacks as the existing test Recorder.
- **Scenarios mirror the existing examples.** Each domain example test gets a parameterised Scenario counterpart whose default parameters reproduce the example exactly. The example tests stay unchanged and remain the README's readable references. Concept pages get their own small Scenarios.
- **Web app.** Uses `@umriss-ui/core` for layout, controls and theming and `@umriss-ui/charts` for the time series, both from npm at a pinned version. It does not reuse umriss-ui's internal demo shell (`@umriss-ui/demo` is private) and does not join the umriss-ui workspace. Event bands, lifecycle marks and drawing a Channel are built on top of the chart, using whatever the charts package offers for overlays. If it offers too little, that is a finding for umriss-ui, not a reason to add a second chart library.
- **Running.** The compiled module is loaded once per page. Every control change calls `run` synchronously on the main thread, which is expected to finish in well under a frame for the Scenario sizes involved (thousands of Samples, rbed's measured throughput is millions of Samples per second). A Web Worker is not part of this spec.
- **Addresses.** Each page is a path of its own (like umriss-ui's ADR-0037). Parameter values that differ from the defaults are stored in the query string.
- **Build-time output, like umriss-ui:** a prerendered HTML file per page containing its text, its source code and its Event table at default parameters (produced by running the same Scenarios on the JVM at build time), a Markdown twin per page, `llms.txt`, a sitemap, a search index, a sharing image, and light and dark themes.
- **Deployment.** A new GitHub Actions workflow builds the library, the Scenario module and the web app, and deploys to GitHub Pages on every push to main and on manual dispatch, following umriss-ui's Pages workflow. The site URL goes into the README.
- **An ADR records** that the documentation site runs rbed in the browser through TeaVM and that the library takes no site-specific code. Next free number: 0005.

## Testing Decisions

- **Good tests here check outcomes a reader can see:** which Events, with which timestamps and flags, come out of a Scenario for given parameters. They do not test how the bridge builds JSON, how the web app lays out a chart or how TeaVM compiles.
- **One seam, run on two runtimes.** Everything is tested through the bridge (`scenarios()` and `run(...)`). No other new seam is introduced.
  - **On the JVM (JUnit, in the Scenario module's build):** for every domain Scenario at default parameters, the Events equal the expected list of the corresponding example test, in the same `"Name start..end flags"` form that `Samples.describe` produces. A few parameter changes are pinned per concept Scenario, for example raising the duration on the lifecycle Scenario turns a Confirmed Event into a Discarded one, and an invalid parameter returns rbed's error message instead of a result.
  - **In Node, against the compiled JavaScript:** for every Scenario at default parameters and at the pinned parameter changes, the result equals the JVM's result for the same input. Numbers are compared as parsed values, not as text, because TeaVM's and the JVM's double-to-text conversions may differ in format. This test runs in the Pages workflow before deployment and blocks it on failure.
- **Web app:** a single browser smoke test (Playwright, as umriss-ui uses) opens one concept page and one domain page, moves one control, and checks that the Event table changes. Further UI tests are not part of this spec.
- **Prior art:** the domain example tests and `Samples.describe` (expected Event lists as text), the test `Recorder` (lifecycle callbacks as lines), and umriss-ui's Pages workflow and Playwright setup.

## Out of Scope

- Writing or compiling arbitrary Java in the browser (a CheerpJ playground). The parameters of prepared Scenarios are the interaction.
- A text format or DSL for Rules. The Java API is what the documentation documents.
- Uploading CSV files or other data. Drawing a Channel's values covers trying one's own shapes.
- Any change to the rbed library's API or behaviour to suit the site.
- Hosting other than GitHub Pages, a custom domain, analytics.
- Versioned documentation (one site per release). The site shows main.
- Moving the domain example tests or changing their code.
- Running the Detector in a Web Worker.
- Visual regression tests of the site.

## Further Notes

- **Fallback if the TeaVM spike fails:** the same Scenario module runs on the JVM at build time, writes `run` results for the default parameters and for a fixed grid of parameter values as JSON, and the web app reads those instead of calling the compiled module. Drawing Channels and free parameter values are then lost, and stepping through prepared values and every other part of this spec remain.
- The site doubles as a real-world test of `@umriss-ui/charts` for time series with overlaid bands and marks. Gaps found there belong in umriss-ui's tracker.
- The README stays the canonical text. The site's chapter texts may start as excerpts from it. Keeping both in sync by generating one from the other is a possible later step and not decided here.
