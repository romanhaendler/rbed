package rbed;

import java.util.Arrays;

/** The Channels one Rule keeps Aggregates for, index-aligned with its Accumulators. */
final class AggregatedChannels {
  private ChannelState[] channels = new ChannelState[4];
  private int count;

  /** Adds a Channel unless present; returns its index. */
  int indexOf(ChannelState channel) {
    for (int i = 0; i < count; i++) if (channels[i] == channel) return i;
    if (count == channels.length) channels = Arrays.copyOf(channels, count * 2);
    channels[count] = channel;
    return count++;
  }

  int count() {
    return count;
  }

  ChannelState get(int i) {
    return channels[i];
  }
}
