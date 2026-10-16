# 03: Retention Limit for counting Events in a window

**What to build:** The confirmation timestamps behind `count(rule).within(window)` obey the Retention Limit too. When more Confirmed Events fall inside the window than the limit, the oldest timestamps are dropped, the count is a lower bound (at most the limit), `droppedValues()` counts the drops, and the Detector's one-time warning fires if it has not yet. History Events go through the same path, so a large History cannot bypass the limit. Update the README "Memory" table entry. See spec: "Retention Limit", "Overflow behaviour".

**Blocked by:** 02 (Retention Limit for duration windows)

**Status:** done

- [x] With a small limit and more Confirmed Events in the window than the limit, `count(...).within(...)` yields the limit and `droppedValues()` reports the drops
- [x] History with more Events in the window than the limit is dropped to the limit and counted as drops
- [x] The warning names the Rule that uses the count and is still logged only once per Detector
- [x] Below the limit, counts and Events are identical to before
