package rbed;

/** Configures one Rule inside {@link Detector.Builder#rule}. */
public final class RuleBuilder {
  Condition when;

  RuleBuilder() {}

  /** The Conditions under which an Event starts and continues. */
  public RuleBuilder when(Condition condition) {
    when = condition;
    return this;
  }
}
