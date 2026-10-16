# 05: Count Events in a window in constant time

**What to build:** Evaluating `count(rule).within(window)` currently walks back over every confirmation timestamp inside the window, so each evaluation costs time proportional to the Events in the window (up to the Retention Limit). Make it amortized constant time: since time only moves forward, each counting Condition can remember where its window starts in the shared timestamp buffer (as a sequence number of confirmations, so time eviction and Retention Limit drops do not invalidate it) and only move that position forward. Results stay exactly min(true count, Retention Limit), History included. Pushing a Sample stays allocation-free.

**Blocked by:** 01–04 and the code review fix round (all merged on `retention`)

**Status:** done

- [x] Several Conditions counting the same Rule with different windows return the same counts as before (existing tests pass unchanged)
- [x] Counts stay correct across Retention Limit drops and History beyond the limit
- [x] A performance-suite check: with a large number of Confirmed Events inside the window, throughput does not degrade with the number of Events in the window (e.g. 1,000 vs. 100,000 Events in the window within a small factor)
- [x] The steady-state allocation checks still allocate nothing per Sample
