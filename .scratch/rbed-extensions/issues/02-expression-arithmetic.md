# 02: Arithmetic on Expressions

**What to build:** Users can compute with Expressions: `plus`, `minus`, `times`, `dividedBy` against Expressions or constants, and `abs()`. For example, "supply minus return temperature above 5" or "price above 1.05 times its average". Undefined operands and non-finite results are undefined. See spec (3).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] All five operations with Expression and double operands
- [ ] Undefined propagates; division by zero is undefined, never true
- [ ] No allocation per Sample
