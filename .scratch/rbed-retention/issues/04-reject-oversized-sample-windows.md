# 04: Reject averages over more Samples than the Retention Limit

**What to build:** `averageOverSamples(n)` declares its size up front, so instead of dropping, `build()` throws `IllegalArgumentException` when n exceeds the Detector's Retention Limit, naming the Rule and n. Raising `retentionLimit(...)` makes the same Rule build. Mention it in the README "Memory" subsection. See spec: "Fixed windows".

**Blocked by:** 02 (Retention Limit for duration windows)

**Status:** ready-for-agent

- [ ] `averageOverSamples(n)` with n above the limit fails in `build()` with a message naming the Rule and n
- [ ] n equal to the limit builds and works as before
- [ ] Raising the limit with `retentionLimit(...)` lets the same Rule build
