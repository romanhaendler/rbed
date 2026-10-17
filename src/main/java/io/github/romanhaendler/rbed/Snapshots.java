package io.github.romanhaendler.rbed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * The Snapshot format: magic "RBED", format version, a 128-bit hash of the Rule descriptions, one of
 * the watched Expressions, sampling rate, Retention Limit, then the Detector's state, then a CRC-32
 * of everything before it. See ADR-0007.
 */
final class Snapshots {
  private static final byte[] MAGIC = {'R', 'B', 'E', 'D'};
  /** Changes only when the shape of the state changes. */
  static final int FORMAT_VERSION = 1;
  private static final int HASH_BYTES = 16;

  private Snapshots() {}

  static byte[] write(Detector detector) {
    StateWriter w = new StateWriter();
    w.raw(MAGIC);
    w.int32(FORMAT_VERSION);
    w.raw(hash(detector.ruleDescriptions()));
    w.raw(hash(detector.watchedDescriptions()));
    w.float64(detector.samplingRate());
    w.int32(detector.retentionLimit());
    detector.saveState(w);
    byte[] body = w.toByteArray();
    byte[] out = Arrays.copyOf(body, body.length + 4);
    int crc = (int) crc(body, body.length);
    for (int i = 0; i < 4; i++) out[body.length + i] = (byte) (crc >>> (24 - 8 * i));
    return out;
  }

  /** Loads {@code bytes} into a Detector that was just built with the Builder's Rules and options. */
  static void restore(Detector detector, byte[] bytes) {
    int shared = Math.min(bytes.length, MAGIC.length);
    if (!Arrays.equals(Arrays.copyOf(bytes, shared), Arrays.copyOf(MAGIC, shared))) {
      throw new IllegalArgumentException(
          "Not an rbed Snapshot: it does not start with the rbed magic header");
    }
    if (bytes.length < MAGIC.length + 4) throw corrupted();
    int version = int32(bytes, 4);
    if (version != FORMAT_VERSION) {
      throw new IllegalArgumentException("Snapshot has format version " + version
          + ", but this rbed reads format version " + FORMAT_VERSION
          + ". Continue by replaying the stream from the Replay Point instead.");
    }
    if (bytes.length < MAGIC.length + 4 + 4
        || crc(bytes, bytes.length - 4) != (int32(bytes, bytes.length - 4) & 0xFFFFFFFFL)) {
      throw corrupted();
    }
    StateReader r = new StateReader(bytes, bytes.length - 4);
    try {
      r.raw(MAGIC.length + 4);
      if (!Arrays.equals(r.raw(HASH_BYTES), hash(detector.ruleDescriptions()))) {
        throw new IllegalArgumentException("Snapshot was taken with different Rules (a Rule that"
            + " was added, removed, renamed or changed, or a Channel used as another kind): a"
            + " Snapshot can only be restored into a Detector built with the same Rules.");
      }
      if (!Arrays.equals(r.raw(HASH_BYTES), hash(detector.watchedDescriptions()))) {
        throw new IllegalArgumentException("Snapshot was taken with different watched Expressions"
            + " than this Builder watches.");
      }
      double rate = r.float64();
      if (Double.doubleToRawLongBits(rate)
          != Double.doubleToRawLongBits(detector.samplingRate())) {
        throw new IllegalArgumentException("Snapshot was taken with sampling rate " + rate
            + " Hz, but this Builder has " + detector.samplingRate() + " Hz.");
      }
      int limit = r.int32();
      if (limit != detector.retentionLimit()) {
        throw new IllegalArgumentException("Snapshot was taken with Retention Limit " + limit
            + ", but this Builder has " + detector.retentionLimit() + ".");
      }
      detector.loadState(r);
      if (!r.atEnd()) throw new IOException("trailing bytes");
    } catch (IOException e) {
      throw corrupted();
    }
  }

  private static IllegalArgumentException corrupted() {
    return new IllegalArgumentException("Snapshot is corrupted or truncated");
  }

  private static int int32(byte[] b, int at) {
    return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8
        | (b[at + 3] & 0xFF);
  }

  private static long crc(byte[] b, int length) {
    CRC32 crc = new CRC32();
    crc.update(b, 0, length);
    return crc.getValue();
  }

  /**
   * A 128-bit fingerprint of the strings (each preceded by its length, so no two lists hash alike
   * by accident): two independent 64-bit FNV-1a style lanes. It guards against a mismatched
   * configuration, not against an adversary, and needs no JDK class beyond java.lang (the
   * compiled browser module has no MessageDigest).
   */
  private static byte[] hash(List<String> lines) {
    long a = 0xcbf29ce484222325L;
    long b = 0x84222325cbf29ce4L;
    for (String line : lines) {
      byte[] utf8 = line.getBytes(StandardCharsets.UTF_8);
      for (int shift = 24; shift >= 0; shift -= 8) {
        a = (a ^ ((utf8.length >>> shift) & 0xFF)) * 0x100000001b3L;
        b = (b ^ ((utf8.length >>> shift) & 0xFF)) * 0x9E3779B97F4A7C15L;
      }
      for (byte x : utf8) {
        a = (a ^ (x & 0xFF)) * 0x100000001b3L;
        b = (b ^ (x & 0xFF)) * 0x9E3779B97F4A7C15L;
        b ^= b >>> 29;
      }
    }
    byte[] out = new byte[HASH_BYTES];
    for (int i = 0; i < 8; i++) {
      out[i] = (byte) (a >>> (56 - 8 * i));
      out[8 + i] = (byte) (b >>> (56 - 8 * i));
    }
    return out;
  }
}
