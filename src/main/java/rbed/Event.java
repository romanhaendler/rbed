package rbed;

import java.util.OptionalLong;

/**
 * An immutable snapshot of a detected occurrence.
 *
 * @param name the name of the Rule that produced it
 * @param start timestamp (ms) of the first Sample at which the Rule's Conditions held
 * @param end timestamp (ms) at which the Event ended; empty until Ended
 * @param truncatedStart the Event was already in progress at the Detector's first Sample
 * @param truncatedEnd observation stopped (close or max gap) while the Event was in progress
 */
public record Event(
    String name, long start, OptionalLong end, boolean truncatedStart, boolean truncatedEnd) {}
