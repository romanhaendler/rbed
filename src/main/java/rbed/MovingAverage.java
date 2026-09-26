package rbed;

import java.util.ArrayDeque;

/**
 * Moving average of a Channel over its last {@code n} values or last {@code millis}. The window
 * itself lives in the Detector, so one Condition can be used by several Detectors.
 */
final class MovingAverage extends Expression {
  final ChannelExpression channel;
  private final int n;
  private final long millis;

  MovingAverage(ChannelExpression channel, int n, long millis) {
    super(channel);
    this.channel = channel;
    this.n = n;
    this.millis = millis;
  }

  @Override
  double value(Detector d) {
    return d.window(this).average();
  }

  Window newWindow() {
    return new Window();
  }

  /** Values of the Channel within the window; memory is bounded by the window size. */
  final class Window {
    private final ArrayDeque<double[]> values = new ArrayDeque<>(); // {time, value}
    private double sum;
    private long firstTime = Long.MIN_VALUE;

    void add(long time, double value) {
      if (firstTime == Long.MIN_VALUE) firstTime = time;
      values.addLast(new double[] {time, value});
      sum += value;
      while (n > 0 ? values.size() > n : values.peekFirst()[0] < time - millis) {
        sum -= values.removeFirst()[1];
      }
    }

    double average() {
      if (values.isEmpty()) return Double.NaN;
      boolean warm = n > 0 ? values.size() == n : values.peekLast()[0] - firstTime >= millis;
      return warm ? sum / values.size() : Double.NaN;
    }
  }
}
