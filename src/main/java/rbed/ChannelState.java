package rbed;

import java.util.Arrays;

/** One Channel inside one Detector: its last two values and the moving-average windows it feeds. */
final class ChannelState {
  final String name;
  double value = Double.NaN;
  double previous = Double.NaN;
  private long time;
  private long previousTime;
  private Window[] windows = new Window[0];

  ChannelState(String name) {
    this.name = name;
  }

  void feed(Window window) {
    windows = Arrays.copyOf(windows, windows.length + 1);
    windows[windows.length - 1] = window;
  }

  void add(long t, double v) {
    previous = value;
    previousTime = time;
    value = v;
    time = t;
    for (Window window : windows) window.add(t, v);
  }

  /** Change per second between the last two values; NaN until there are two. */
  double rate() {
    return (value - previous) * 1000.0 / (time - previousTime);
  }
}
