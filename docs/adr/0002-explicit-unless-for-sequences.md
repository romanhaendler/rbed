# Sequences use explicit `unless(...)`, not "the latest Event was A"

A Rule that depends on an earlier Event A is expressed as `after(A)`, optionally narrowed with `unless(C, ...)` (no C since A) and `within(window)`. We rejected the simpler-looking semantics "the most recent Event of any Rule was A", because it couples every Rule to every other: adding an unrelated Rule would silently change when existing sequence Rules fire. Naming the interrupting Events explicitly keeps each Rule's behaviour local and stable.
