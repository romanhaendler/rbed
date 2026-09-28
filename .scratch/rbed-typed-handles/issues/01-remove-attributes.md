# 01: Remove Rule attributes

**What to build:** Rules no longer have attributes; Ended Events carry no attribute map. Derived values of an Event (distance, classification, peak) are plain methods of the Event written by the user. This shrinks the API before the handle migration touches it. See spec, user story 24.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] The Rule configuration has no `attribute(...)`; Events have no `attributes()`
- [x] Tests and examples that used attributes compute the same values with plain methods; their expected results are unchanged
- [x] The README's attributes section is replaced by a short example of a plain method on an Event, and the Event table no longer lists attributes
- [x] All tests pass
