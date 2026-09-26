# 10: Examples use the new features

**What to build:** The examples replace their workarounds with the new features: VitalSigns gains a SensorLost Rule via `age()`/`advanceTo`, HvacShortCycling counts starts per hour, ColdChain limits total time above 8 °C, FleetTelematics uses the integral for distance, FloodWarning uses a smoothed rate, DeploymentRegression uses a Pulse Channel, IntervalTraining uses `latest(...)`, and the crane detects a partial set-down. `gaps.md` and the README record what was resolved.

**Blocked by:** 01–09

**Status:** ready-for-agent

- [ ] Every gap in `gaps.md` has a resolved example or is marked as still open with a reason
- [ ] README documents all new API
