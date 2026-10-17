package io.github.romanhaendler.rbed;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Writes the primitive values of a Snapshot; doubles as raw bits, so NaN and -0.0 survive. */
final class StateWriter {
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final DataOutputStream out = new DataOutputStream(bytes);

  void bool(boolean v) {
    try { out.writeBoolean(v); } catch (IOException e) { throw new UncheckedIOException(e); }
  }

  void int32(int v) {
    try { out.writeInt(v); } catch (IOException e) { throw new UncheckedIOException(e); }
  }

  void int64(long v) {
    try { out.writeLong(v); } catch (IOException e) { throw new UncheckedIOException(e); }
  }

  void float64(double v) {
    int64(Double.doubleToRawLongBits(v));
  }

  void raw(byte[] v) {
    try { out.write(v); } catch (IOException e) { throw new UncheckedIOException(e); }
  }

  void float64s(double[] v) {
    int32(v.length);
    for (double d : v) float64(d);
  }

  void int64s(long[] v) {
    int32(v.length);
    for (long l : v) int64(l);
  }

  void bools(boolean[] v) {
    int32(v.length);
    for (boolean b : v) bool(b);
  }

  byte[] toByteArray() {
    return bytes.toByteArray();
  }
}
