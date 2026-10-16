# 02: Retention Limit for duration windows

**What to build:** `Detector.builder().retentionLimit(n)` (n >= 1, default 1,000,000) caps every `averageOverDuration` window. When a full window gets a new value, its oldest entry is dropped like one leaving the window by time, so the average stays the mean of the entries that remain and warm-up is unaffected. Detection continues. `Detector.droppedValues()` reports the entries dropped because of the limit (not those leaving by time). The first drop in a Detector logs one `WARNING` through `System.Logger` (logger named after the package) naming the Rule and the limit and pointing to `retentionLimit(...)`; later drops do not log. Record ADR-0004, add **Retention Limit** to CONTEXT.md, and extend the README "Memory" subsection. See spec: "Retention Limit", "Overflow behaviour", "Warning", "ADR-0004", "Glossary".

**Blocked by:** 01 (Ignore unused Channels and report retained values)

**Status:** ready-for-agent

- [ ] With a small limit on a dense stream, `retainedValues()` for the window stays at the limit and `droppedValues()` counts the evictions
- [ ] The average equals the mean of the most recent limit-many values
- [ ] Below the limit, Events are identical to before and `droppedValues()` is 0
- [ ] The warning is logged exactly once per Detector and names the Rule and the limit (asserted with a `java.util.logging` handler on the package logger)
- [ ] `retentionLimit(0)` and negative values are rejected by the builder
- [ ] A performance-suite allocation check: a full duration window that drops an entry every Sample allocates nothing per Sample
- [ ] ADR-0004, the CONTEXT.md glossary entry and the README are updated; the module descriptor gains no `requires`
