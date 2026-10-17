package io.github.romanhaendler.rbed;

/**
 * The longest time window and the largest Sample count a Detector's Rules and watched Expressions
 * use by configuration (see {@link Detector#memoryHorizon}). A configuration bound only: Events in
 * progress, Rules waiting for {@code when} to stop holding and {@code latest(...)} can reach
 * further back; the Replay Point is the exact answer.
 *
 * @param millis the longest time window in milliseconds, from {@code forDuration}, End Condition
 *     durations, {@code averageOverDuration}, {@code within(...)} and {@code maxGap}; 0 if none
 * @param samples the largest Sample count, from {@code forSamples} and
 *     {@code averageOverSamples}; 0 if none
 */
public record MemoryHorizon(long millis, int samples) {}
