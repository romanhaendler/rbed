# 09: Total duration of Events

**What to build:** `totalDuration("Excursion")` is an Expression in ms with the summed duration of all Ended Excursions since start (History included) plus the running one so far, so a cumulative limit like "more than 30 minutes above 8 °C in total" is a Rule. See spec (5).

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Sums Ended Events and the running Confirmed one up to now
- [x] Includes History durations
- [x] Reference validation as for `after`
