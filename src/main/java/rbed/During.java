package rbed;

/** True while an Event with the given name is Confirmed and has not Ended. */
final class During extends Condition {
  final String name;

  During(String name) {
    this.name = name;
  }

  @Override
  Boolean test(Detector d) {
    return d.isRunning(name);
  }
}
