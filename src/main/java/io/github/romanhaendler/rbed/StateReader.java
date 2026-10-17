package io.github.romanhaendler.rbed;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/** Reads what {@link StateWriter} wrote; any inconsistency is an {@link IOException}. */
final class StateReader {
  private final ByteArrayInputStream bytes;
  private final DataInputStream in;

  StateReader(byte[] data, int length) {
    bytes = new ByteArrayInputStream(data, 0, length);
    in = new DataInputStream(bytes);
  }

  boolean bool() throws IOException {
    return in.readBoolean();
  }

  int int32() throws IOException {
    return in.readInt();
  }

  long int64() throws IOException {
    return in.readLong();
  }

  double float64() throws IOException {
    return Double.longBitsToDouble(in.readLong());
  }

  byte[] raw(int n) throws IOException {
    byte[] v = new byte[n];
    in.readFully(v);
    return v;
  }

  /** An element count: not negative and not more than the bytes left (each element takes one). */
  int length() throws IOException {
    int n = in.readInt();
    if (n < 0 || n > bytes.available()) throw new IOException("bad length " + n);
    return n;
  }

  /** Reads a count that the configuration determines; a different one means a wrong Snapshot. */
  void expect(int expected) throws IOException {
    if (in.readInt() != expected) throw new IOException("unexpected count");
  }

  double[] float64s() throws IOException {
    double[] v = new double[length()];
    for (int i = 0; i < v.length; i++) v[i] = float64();
    return v;
  }

  long[] int64s() throws IOException {
    long[] v = new long[length()];
    for (int i = 0; i < v.length; i++) v[i] = int64();
    return v;
  }

  boolean[] bools() throws IOException {
    boolean[] v = new boolean[length()];
    for (int i = 0; i < v.length; i++) v[i] = bool();
    return v;
  }

  boolean atEnd() {
    return bytes.available() == 0;
  }
}
