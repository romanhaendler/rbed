# 04: Scenario page kit

**What to build:** Every page that shows a Scenario offers the same tools, demonstrated on the front page's first Rule: the Java source of the Rules with the current parameter values filled in and a copy button; a reset to the default parameters; non-default parameter values in the query string, so a link reproduces what the reader sees; a hover readout of Channel values and Rule states at a timestamp; toggles to show or hide individual Rules and Channels; an Event table (name, start, end, Truncated flags) whose rows and the chart's bands highlight each other; rbed's own error message shown when parameters are invalid; a text alternative for every chart listing its Events; every control operable by keyboard. See spec: User Stories 28 to 38, 45, 46.

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] The shown Java source contains the current parameter values and updates as controls move; copy puts it on the clipboard
- [ ] Reset restores the defaults and clears them from the query string
- [ ] Opening a link with parameters in the query string shows exactly that state
- [ ] Hovering the chart shows Channel values and Rule states at that timestamp
- [ ] Rules and Channels can be hidden and shown
- [ ] Selecting a table row highlights its band, and selecting a band highlights its row
- [ ] An invalid parameter combination shows rbed's error message in place of the result
- [ ] Each chart has a text alternative listing its Events; all controls are reachable and operable by keyboard
- [ ] The result updates within a frame or two of moving a slider
