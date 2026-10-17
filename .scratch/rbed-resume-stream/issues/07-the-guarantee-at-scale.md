# 07: The restore guarantee at scale

**What to build:** Evidence that a restore never differs, across everything the library can express. The generator from ticket 02 runs thousands of random Rule sets and inputs from every construct; for each, Snapshots are taken at every moment and at random moments, chains of restores (after every single moment) run to the end, and every round trip is byte-identical. A final teeth audit lists every piece of state the Snapshot writes and shows a failing test for each when omitted or corrupted. Snapshot size stays bounded by the Retention Limit; Snapshot time and size are noted for the performance tests' realistic Rule set. See spec: Testing Decisions "Differential testing of the guarantee", "Snapshot contract tests", "Performance".

**Blocked by:** 04, 05, 06

**Status:** ready-for-agent

- [ ] Random cases (regular build: thousands with fixed seeds; extended profile: far more with fresh seeds) never differ between the uninterrupted run and runs with one Snapshot at every moment, with Snapshots at random moments, and with a restore after every single moment
- [ ] Every Snapshot of a restored Detector is byte-for-byte identical to the one it was restored from, over all generated cases
- [ ] The extended profile has been run locally with a large number of fresh seeds without a difference (count and duration recorded in Comments)
- [ ] Teeth audit: a list in the Comments of every piece of state the Snapshot writes, each with the test that fails when it is omitted or corrupted; no piece without one
- [ ] Snapshot size is bounded by the Retention Limit (test with windows at the limit); Snapshot time and size for the realistic Rule set are recorded
- [ ] Pushing a Sample still allocates nothing more than before (existing allocation checks)
- [ ] Every regression found on the way is a named JUnit case
- [ ] `mvn -q verify` passes
