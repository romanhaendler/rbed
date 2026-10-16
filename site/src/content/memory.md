# Memory and the Retention Limit

What a Detector holds in memory follows from its Rules, not from how long it runs. Values of Channels that no Rule uses are ignored and leave no trace.

## What a Detector retains

- A Channel used by a Rule: its latest value.
- `averageOverSamples(n)`: the last `n` values. `build()` rejects an `n` above the Retention Limit.
- `averageOverDuration(w)`: every value within `w`, at most the Retention Limit.
- `count(...).within(w)`: one timestamp per Confirmed Event within `w`, at most the Retention Limit, History included.
- Everything else: a constant amount per Rule. A running Event keeps Aggregates only for the Channels its Rule aggregates, so memory stays bounded however long an Event lasts.

`detector.retainedValues()` returns the number of entries held right now (Channel values, moving-average values and count timestamps). It is a plain counter read, cheap enough to poll as a metrics gauge.

## The Retention Limit

The **Retention Limit** caps every `averageOverDuration` window and every `count(...).within(...)` list of confirmation timestamps. It is 1,000,000 entries by default, about 16 MB per window; set it with `Detector.builder().retentionLimit(n)`.

When a full window gets a new value, its oldest entry is dropped as if it had left the window by time. Detection continues:

- the moving average is the mean of the entries that remain;
- a count becomes a lower bound: it is at most the limit, so a Condition with a threshold at or above the limit, such as `count(run).within(...).above(limit)`, can never hold.

The first drop logs one `WARNING` through `System.Logger`, naming the Rule and the limit. `detector.droppedValues()` returns how many entries were dropped because of the limit (not those that left a window by time), for example to alert on.

The [Errors and validation](../errors-and-validation/) page shows what `build()` and `retentionLimit` reject: an average over more Samples than the Retention Limit, and a limit below one.

## Reference

- [Memory](https://github.com/romanhaendler/rbed#memory) and [Performance](https://github.com/romanhaendler/rbed#performance) in the README, the canonical text
- [Detector](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html) and [Detector.Builder](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html) in the Javadoc

Methods used: [`ChannelExpression.averageOverSamples`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverSamples%28int%29), [`ChannelExpression.averageOverDuration`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/ChannelExpression.html#averageOverDuration%28java.time.Duration%29), [`Conditions.count`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Conditions.html#count%28io.github.romanhaendler.rbed.Rule%29), [`EventCount.within`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/EventCount.html#within%28java.time.Duration%29), [`Detector.builder`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#builder%28%29), [`Detector.Builder.retentionLimit`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#retentionLimit%28int%29), [`Detector.Builder.build`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#build%28%29), [`Detector.retainedValues`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#retainedValues%28%29), [`Detector.droppedValues`](../api/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#droppedValues%28%29).
