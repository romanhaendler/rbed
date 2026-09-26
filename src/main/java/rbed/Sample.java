package rbed;

import java.util.Map;

/**
 * The values of one or more Channels at a single timestamp, for {@link Detector#detect}.
 *
 * @param timestamp milliseconds
 * @param values values of any subset of Channels
 */
public record Sample(long timestamp, Map<String, Double> values) {}
