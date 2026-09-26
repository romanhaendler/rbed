# 07: Values of the latest Event in Conditions

**What to build:** `latest("A").mean(ch) / min(ch) / max(ch) / duration()` are Expressions over the most recent Confirmed Event of A, running or Ended, so users can detect "weight fell below half of the lifted load" (`channel("weight").below(latest("LoadLifted").mean("weight").times(0.5))`). Referenced Channels are aggregated by A automatically. See spec (2).

**Blocked by:** 01 (Aggregates only for the Channels a Rule uses), 02 (Arithmetic on Expressions)

**Status:** ready-for-agent

- [ ] Values of a running Event update as it runs; after it Ends they stay until the next one is Confirmed
- [ ] Undefined before any Confirmed Event; History gives `duration()` but no channel values
- [ ] Self/forward references rejected at build
- [ ] Partial set-down crane test
