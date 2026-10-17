# 04: Site reads its plotted Expressions from rbed

**What to build:** The documentation site reads every plotted Expression through `watch`/`value` instead of the bisection workaround. The Scenario module watches each plotted Expression and reads it after every push and every time advance in the shared feeding helpers, so a plot costs one run and also has values at advance moments (the silent sensors page shows `hr.age()` growing during the silence). The bisection code and its bisection-only tests are removed. The Conditions and Expressions page gains a short "Read an Expression's value" passage with a Java snippet that SourceTest compiles and runs. ADR-0005's note on reading Expression values and the site README are updated. See spec: Solution 3 (last sentence); Implementation Decisions "Documentation site"; Testing Decisions "Site tests".

**Blocked by:** 03

**Status:** done

- [x] Plotted Expressions are read through `watch`/`value`; the bisection code is gone
- [x] BridgeTest's existing assertions on plotted values pass unchanged at every Sample (exact equality with the former values)
- [x] Plotted values now include advance moments; new BridgeTest assertions pin them on the silent sensors Scenario
- [x] The silent sensors chart shows `hr.age()` growing across the silence, and its text alternative and readout agree with the line
- [x] The Conditions and Expressions page has a "Read an Expression's value" passage with a snippet that SourceTest compiles and runs, and Javadoc links to the new methods that resolve against the generated Javadoc
- [x] ADR-0005 and site/README.md describe the new way of reading values; no mention of the bisection is left
- [x] Run time of a Scenario with a plotted Expression drops measurably (record before/after in this ticket's Comments)
- [x] `mvn -q verify` at the root; in site/: `npm ci`, `npm run build`, `npm test`, `CI=1 PORT=<free port> npm run test:browser` pass; every page at 1280/375, light/dark, has no page errors and no axe violations

## Comments

- How it works: `Bridge.run` creates `new Lifecycle(plotted)` with the plots' Expressions. `lifecycle.builder()` is `Detector.builder().listener(this).watch(plotted)`, and `push`/`pushValues`/`advance` read every plotted Expression right after feeding into `atSamples`/`atAdvances` (one `double[]` per moment, NaN where undefined, `+ 0.0` so -0.0 is plotted as 0 as the bisection did). Every `detect` (the default and each override) now builds its Detectors from `lifecycle.builder()`; Sports, with a listener of its own, still calls `.listener(...)` after it and passes every callback on. A Scenario that builds with `Detector.builder()` directly fails its run with "Expression ... is not watched".
- `ExpressionValues` is gone. Of its tests, `aScenarioThatAdvancesTimeBeforeItsFirstSampleCannotBePlotted` became `anAdvanceBeforeTheFirstSampleIsPlottedAsUndefined` (now possible: null at that advance, then values, a held Channel after the Sample); `plottedValuesAreExactlyTheDoublesRbedComputes` stays, now through `Bridge.run` (bit-exact doubles, -0.0 as 0, undefined as null).
- Exactness: every Scenario at its defaults and at every option of every choice (39 runs) gives identical results before and after in the compiled module (compared exactly, with `Object.is`), including `values` and `atAdvances` of every plot. BridgeTest's plot assertions pass unchanged.
- Deviation: values at advances are not new output. The bisection already followed advances (the k-th moment after a Sample had probe Channels of its own), and `silentSensorsPlotsTheAgeOfTheHeartRateAtEverySampleAndEveryAdvance` already pinned `hr.age()` growing from 1 to 20 s across the silence, which the chart, its text alternative and readout already showed. The new assertion on advances is the advance before the first Sample, which the bisection could not plot.
- Run time per `run(...)` in Node (compiled module, default parameters, mean of 200 runs after 20 warm-up runs), before -> after: expressions 4.95 -> 0.56 ms, count 3.70 -> 1.10 ms, total-duration 5.05 -> 1.57 ms, silent-sensors 6.80 -> 0.29 ms, latest 2.48 -> 0.67 ms, finance (two plots) 67.7 -> 4.9 ms, cranes 1.35 -> 0.36 ms; first-rule (no plot) 0.044 -> 0.048 ms. The compiled module shrank from 214 KB to 210 KB.
- The page passage is "## Read an Expression's value" on the Conditions and Expressions page (anchor `#read-an-expression-s-value`, linked from the live paragraph). Its snippet is complete (imports, statements). `SourceTest.theReadAnExpressionsValueSnippetCompilesAndPrintsTheValues` takes the first java fence after that heading, compiles it against rbed, runs it and checks its four printed lines; `SourceTest.compile(units, out)` is now the shared compile-and-load helper.
- The smoke tests have no axe or dark-mode pass; checked once with a throwaway script (axe-core 4.10.2 installed with `--no-save`, nothing committed): every page at 1280/375 px, light and dark, has no page error and no axe violation.
