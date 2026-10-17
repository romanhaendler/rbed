package io.github.romanhaendler.rbed;

/**
 * The canonical encoding of a configuration, from which the Snapshot fingerprint is computed.
 * Unlike the readable {@code toString} it is injective: every name is length-prefixed, every
 * number is delimited, and every construct is a tag (letters only) with its parts in parentheses,
 * so no choice of names (dots, commas, quotes, parentheses, "pulse", digits) can make two
 * different configurations produce the same text.
 */
final class Key {
  private Key() {}

  /** A name: its length in chars, a colon, the chars. */
  static String name(String name) {
    return name.length() + ":" + name;
  }

  static String number(long value) {
    return "n" + value + ";";
  }

  /** The exact bits, so that 0.0 and -0.0, and every threshold, stay distinct. */
  static String number(double value) {
    return "d" + Long.toHexString(Double.doubleToRawLongBits(value)) + ";";
  }

  /** A construct: {@code tag(part part ...)}; every part is itself delimited. */
  static String node(String tag, String... parts) {
    return tag + "(" + String.join("", parts) + ")";
  }
}
