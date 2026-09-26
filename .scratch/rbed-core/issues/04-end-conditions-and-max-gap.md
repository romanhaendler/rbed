# 04: End Conditions and max gap

**What to build:** A user can give a Rule a separate End Condition with its own duration, so that an Event started by "weight > 500" only ends when "weight < 50 for 2 s" (hysteresis). The Event's end is the timestamp where the end phase began, and the end phase resets if the End Condition stops holding. A user can set `maxGap`: a gap between Samples larger than it Discards a Pending Event, and Ends a Confirmed Event with truncatedEnd at the last Sample before the gap.

**Blocked by:** 03 (Durations and the Pending phase)

**Status:** done

- [x] With `endWhen`, the Event continues while the start Condition is false but the End Condition is not yet satisfied
- [x] End Condition durations (Samples or time) are honoured, and the end phase resets on interruption
- [x] End = timestamp at which the end phase began
- [x] `maxGap` Discards Pending Events and truncates Confirmed ones as specified; evaluation continues with the current Sample
