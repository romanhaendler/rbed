# 04: Scenario page kit

**What to build:** Every page that shows a Scenario offers the same tools, demonstrated on the front page's first Rule: the Java source of the Rules with the current parameter values filled in and a copy button; a reset to the default parameters; non-default parameter values in the query string, so a link reproduces what the reader sees; a hover readout of Channel values and Rule states at a timestamp; toggles to show or hide individual Rules and Channels; an Event table (name, start, end, Truncated flags) whose rows and the chart's bands highlight each other; rbed's own error message shown when parameters are invalid; a text alternative for every chart listing its Events; every control operable by keyboard. See spec: User Stories 28 to 38, 45, 46.

**Blocked by:** 03

**Status:** done

- [x] The shown Java source contains the current parameter values and updates as controls move; copy puts it on the clipboard
- [x] Reset restores the defaults and clears them from the query string
- [x] Opening a link with parameters in the query string shows exactly that state
- [x] Hovering the chart shows Channel values and Rule states at that timestamp
- [x] Rules and Channels can be hidden and shown
- [x] Selecting a table row highlights its band, and selecting a band highlights its row
- [x] An invalid parameter combination shows rbed's error message in place of the result
- [x] Each chart has a text alternative listing its Events; all controls are reachable and operable by keyboard
- [x] The result updates within a frame or two of moving a slider

## Comments

**Done.** Checked in headless Chromium (Playwright from a scratch directory, as in 03) at 1280 and 375 px, light and dark, on the front page: no horizontal scroll, no page errors. Source and copy, address, reset, link, hover readout, Rule and Channel toggles, row and band selection (mouse and Enter on the chart), the text alternative, the error message and the Tab order were all scripted. A slider step updates the source within 2 ms.

**The kit is `ScenarioView` (site/src/ScenarioView.tsx), one `scenario` prop in.** It holds the parameters (from and to the address), the hidden Rules and Channels, and the selected Event, runs the bridge in a `useMemo`, and renders: the configurator card (chart stage with a visually hidden Event summary, panel with "Parameters" + Reset, a Slider per parameter, a "Show" fieldset with a Checkbox per Rule and Channel, Java source under the stage) and the Event table below. Extension points for later tickets:
- `EventChart` takes `shown` (`{scenario, rows, events, channels, rules}`, the visible ones only). Lifecycle marks (05), Expression lines (07) and Pulse marks (09) go in there as further series; JSX order is draw order. `Readout` is the tooltip; add rows to it for new series. It reads `shown` through a ref because umriss compares `render` by source text.
- The chart's `key` is the Scenario id plus the shown Rules, since the lane axis' `tickFormat` closes over the Rule names. Anything else a `tickFormat` or `render` closes over has to go through the ref or the key.
- Hiding a Rule removes its lane; hiding a Channel keeps the course axis (computed from all Channels), so nothing jumps.
- Colours: Channels take `var(--uc-series-1,3,4,5,6)`, Events `--uc-series-2`. Those variables exist only inside the chart's root, so `site.css` repeats the palette on `.configuratorPanel` for the swatches.
- Selection: an Event's key is `name start`. Band rows carry it (`BandRow.key`); `onSelect` picks the first hit point with a segment whose datum has `s === 0` (a click on a hole or no band clears). The selected Event is drawn as a `LimitBand orientation="x"` in the accent colour and its row gets `data-selected`; the row's name is a toggle button (`aria-pressed`).
- Aggregates columns (09) go into `EventTable`; Discarded Events (05) probably into both the chart and the table.

**Address:** `src/parameters.ts` (`fromQuery`, `toQuery`, tested in `test/parameters.test.mjs`). Only non-default values are written, with the parameter id as key; other query keys stay. The address follows 250 ms after the last change (`replaceState`; browsers throttle it, and a slider moves many times a second). A value that is not a finite number becomes NaN, which reaches rbed as JSON null and shows its message (on the first Rule: `?threshold=abc` gives "Limit is NaN"; the sliders cannot produce an invalid value there, so the address is the only way to see the error on the front page). The source then shows `Double.NaN`. Two Scenarios on one page would share keys for equal parameter ids; prefix them if a page ever shows two.

**Java highlighting:** `src/java.ts` (`javaTokens`, a regex tokenizer for comments, strings and text blocks, annotations, numbers, keywords and capitalised names; tested in `test/java.test.mjs`). `CodeBlock` uses it for `language="Java"`; colours in `site.css` under `.code .java-*`, mapped to tokens as umriss maps sugar-high's. `tsconfig.json` lib is now ES2023 (for `findLast`).

**Errors:** an `Alert tone="danger"` titled "rbed rejects these parameters" with rbed's message replaces the chart; the Event table is hidden then.

- **umriss-ui gaps:**
  - No hover callback (known): the readout lives in the Tooltip's `render`, using the `uc-tooltip-head/list/row/chip/name/value` classes so it looks like the built-in one; those class names are internal.
  - A `StateBand` answers the pointer only over its own lane, even with `Tooltip mode="x"`: with every Channel hidden, the readout and band selection work only while the pointer is over a lane.
  - The legend cannot be made per band or bound to app state (a state entry hides every band carrying that state), so the chart has no `Legend`; the "Show" checkboxes with colour swatches stand in for it.
  - The series palette variables (`--uc-series-N`) are defined on `.uc-root` only, so an app that wants matching swatches outside the chart has to copy them.
  - No way to add one's own text to the chart's accessible description: the Event summary is a `VisuallyHidden` paragraph after the chart.
