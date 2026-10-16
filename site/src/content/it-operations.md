# IT operations

A web service reports its errors per minute and its CPU load every minute. A further Channel reports deployments; it is a Pulse Channel, so its value is set only in the Sample carrying a deployment, here at minute 10. An error spike soon after a deployment is probably caused by it.

<!-- scenario it-operations -->

## The Rules

- **Deployment**: a deployment is reported. It Ends with the next Sample, which carries no deployment.
- **ErrorSpike**: more than 5 errors per minute for 2 minutes.
- **Regression**: an error spike within 30 minutes after a Deployment. The spike at minute 25 is 14 minutes after it; the one at minute 80 is too long after it, so it is no Regression.
- **HighCpu**: the CPU above 90 % for 5 minutes.

## Source and reference

The example is [DeploymentRegressionExample](https://github.com/romanhaendler/rbed/blob/main/src/test/java/io/github/romanhaendler/rbed/examples/DeploymentRegressionExample.java), listed in the README's [Examples](https://github.com/romanhaendler/rbed#examples) with its expected Events.

Methods used: [`Channel.pulse`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Channel.html#pulse%28java.lang.String%29), [`Rule.when`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`forDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`above`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`and`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Condition.html#and%28io.github.romanhaendler.rbed.Condition%29), [`after`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#after%28io.github.romanhaendler.rbed.Rule%29), [`After.within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/After.html#within%28long%29).

The concepts at work, each on a page of its own: [Pulse Channels](../pulse-channels/), [After another Event](../after/).
