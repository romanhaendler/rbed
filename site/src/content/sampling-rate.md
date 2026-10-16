# Batch processing and sampling rates

A recorded series does not need a loop of `push` calls: `detect` runs it through a fresh Detector in one call. And regularly sampled data needs no timestamps at all: declare the sampling rate, and the Detector derives each Sample's time from its position.

## A recording in one call

`detect` pushes every Sample, closes the Detector and returns every Ended Event, in the order they Ended:

```java
Rule loadLifted = Rule.named("LoadLifted").when(weight.above(500));
Detector detector = Detector.builder()
    .rules(loadLifted)
    .build();

List<Event> events = detector.detect(List.of(
    new Sample(0,     Map.of(weight, 0.0)),
    new Sample(500,   Map.of(weight, 1200.0)),
    new Sample(1_000, Map.of(weight, 0.0))));   // [LoadLifted 500..1000]
```

It accepts any `Iterable<Sample>`, so a file read lazily works as well. A listener, if configured, is still called. A Detector can be used only once: `detect` on a Detector that has already seen Samples throws.

## Samples without timestamps

For regularly sampled data without timestamps, declare the rate on the builder with `samplingRate(hz)` and push only the values, with `push(values)` or, for a whole recording, `detectValues`. Sample `i` (counting from 0) is then at `round(i * 1000 / hz)` milliseconds: at 50 Hz, the Samples are 20 ms apart.

The rate is at most 1000 Hz, since timestamps have millisecond resolution. With a sampling rate, `push(timestamp, values)` and `advanceTo` throw, because time advances with the Samples; without one, `push(values)` throws.

## Durations count positions, not clock time

With a sampling rate, every duration is measured in Sample positions converted to time. That is exact for truly regular data, and wrong by exactly as much as the data is not regular.

Below, an accelerometer was logged at 10 Hz, but the logger lost the Samples from 2.1 to 2.9 s. `Vibration` requires `accel` above 3 for 1.5 s. Pushed with their timestamps, both vibrations last long enough: the first from 1.5 to 3.5 s, Confirmed at 3 s. Choose "Values at a sampling rate", and the same Samples are pushed as values with `detectValues` at a declared 10 Hz: every Sample after the loss now lies 0.9 s early. The first vibration seems to stop at 2.6 s, before its 1.5 s are reached, and is Discarded; the second is still Confirmed, but 0.9 s earlier than it happened.

<!-- scenario sampling-rate draw -->

Declare a different rate, and every duration stretches or shrinks with it: at 5 Hz the Samples are 200 ms apart, so the first vibration lasts 2.2 s and is Confirmed again, at 4.6 s, although on the clock it lasted 2 s. `forSamples` counts Samples in either mode, so only `forDuration` and the other time-based parts of a Rule depend on the rate.

Use a sampling rate when the data really is regular and has no timestamps. When Samples can go missing, push them with their timestamps.

## Reference

- [Batch processing and sampling rates](https://github.com/romanhaendler/rbed#batch-processing-and-sampling-rates) in the README, the canonical text
- [Detector](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html) in the Javadoc: `detect`, `detectValues`, `push` and `Detector.Builder.samplingRate`

Methods used: [`when`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#when%28io.github.romanhaendler.rbed.Condition%29), [`above`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Expression.html#above%28double%29), [`detect`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#detect%28java.lang.Iterable%29), [`forDuration`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Rule.html#forDuration%28long%29), [`samplingRate`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.Builder.html#samplingRate%28double%29), [`detectValues`](https://javadoc.io/doc/io.github.romanhaendler/rbed/latest/io.github.romanhaendler.rbed/io/github/romanhaendler/rbed/Detector.html#detectValues%28java.lang.Iterable%29).
