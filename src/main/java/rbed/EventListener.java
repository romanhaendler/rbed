package rbed;

/**
 * Receives the lifecycle of Events from a {@link Detector}. All methods default to no-ops, so
 * implement only the ones you need. Calls happen synchronously from {@link Detector#push} and
 * {@link Detector#close}.
 */
public interface EventListener {
  /** The Rule's Conditions started holding, but its required duration is not reached yet. */
  default void onPending(Event event) {}

  /** The Conditions held for the required duration; the Event counts as detected. */
  default void onConfirmed(Event event) {}

  /** A Confirmed Event reached its end. */
  default void onEnded(Event event) {}

  /** A Pending Event stopped holding before it was Confirmed. */
  default void onDiscarded(Event event) {}
}
