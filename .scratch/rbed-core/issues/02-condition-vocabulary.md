# 02: Condition vocabulary and sample-and-hold

**What to build:** A user can write Conditions with `below`, `between`, comparisons of one Channel against another, and combine them with `and`, `or` and `not`. Samples may carry any subset of Channels. Each Channel keeps its last known value until its next Sample, and comparisons on a Channel that never received a value are false (also under `not`).

**Blocked by:** 01 (Walking skeleton)

**Status:** done

- [x] `below`, `between` (inclusive bounds) and expression-vs-expression comparisons work
- [x] `and`, `or` and `not` combine Conditions
- [x] Partial Samples use held values for missing Channels
- [x] Undefined comparisons are false, and `not` does not turn them true
