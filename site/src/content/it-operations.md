# IT operations

Every minute, a web service reports its error count and its CPU load. A further Channel reports deployments; it is a Pulse Channel, so its value exists only in the Sample that carries it, here at minute 10. An error spike soon after a deployment is probably caused by it.

<!-- scenario it-operations -->

## The Rules

- **Deployment**: a deployment is reported. It Ends with the next Sample, which carries no deployment.
- **ErrorSpike**: more than 5 errors per minute for 2 minutes.
- **Regression**: more than 5 errors per minute for 2 minutes, all within 30 minutes after a Deployment Ended.
- **HighCpu**: the CPU above 90 % for 5 minutes.

At the defaults, the deployment at minute 10 is a `Deployment` from minute 10 to 11. The errors are above 5 per minute from minute 25 to 35 and from 80 to 85, an `ErrorSpike` each, Confirmed after 2 minutes. The spike at minute 25 starts 14 minutes after the Deployment Ended, so it is a `Regression` as well; the one at minute 80 is too long after it. The CPU is above 90 % from minute 90 to 100, and `HighCpu` is Confirmed at minute 95.

### Things to try

- Narrow *Regression within* to 15 minutes: the spike at minute 25 starts inside the window, but its 2 minutes are not reached before the window closes at minute 26, so the Pending `Regression` is Discarded.

## Reference

The example is [DeploymentRegressionExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/DeploymentRegressionExample.java), a test with its expected Events, listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples).

Methods used: [`Channel.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#named%28java.lang.String%29), [`Channel.pulse`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#pulse%28java.lang.String%29), [`Rule.named`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#named%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`Expression.above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`Rule.forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28java.time.Duration%29), [`Conditions.after`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28java.time.Duration%29), [`Condition.and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.rules`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#rules%28io.github.romanhaendler.rbed.Rule...%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.push`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#push%28long,java.util.Map%29), [`Detector.close`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#close%28%29).

The concepts at work, each on a page of its own: [Pulse Channels](../pulse-channels/), [After another Event](../after/).
