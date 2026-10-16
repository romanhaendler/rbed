package io.github.romanhaendler.rbed.site;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the JSON text that crosses the bridge. Hand-written, because the project has
 * no dependencies and because String.format and regular expressions would make the TeaVM output
 * several times larger.
 */
final class Json {
  private final String text;
  private int pos;

  private Json(String text) {
    this.text = text;
  }

  /** Objects become Maps, arrays Lists, numbers Doubles; null, booleans and strings as is. */
  static Object parse(String text) {
    Json json = new Json(text);
    Object value = json.value();
    json.skipSpace();
    if (json.pos < text.length()) throw json.error("end of input");
    return value;
  }

  /** Appends a number; NaN and the infinities, which JSON cannot express, become null. */
  static StringBuilder number(StringBuilder out, double value) {
    return Double.isFinite(value) ? out.append(value) : out.append("null");
  }

  static StringBuilder string(StringBuilder out, String value) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append("\\u00").append(Character.forDigit(c >> 4, 16))
                .append(Character.forDigit(c & 15, 16));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"');
  }

  private Object value() {
    skipSpace();
    if (pos >= text.length()) throw error("a value");
    char c = text.charAt(pos);
    if (c == '{') return object();
    if (c == '[') return array();
    if (c == '"') return string();
    if (text.startsWith("true", pos)) return literal("true", Boolean.TRUE);
    if (text.startsWith("false", pos)) return literal("false", Boolean.FALSE);
    if (text.startsWith("null", pos)) return literal("null", null);
    return number();
  }

  private Map<String, Object> object() {
    Map<String, Object> map = new LinkedHashMap<>();
    pos++;
    if (next() == '}') {
      pos++;
      return map;
    }
    while (true) {
      if (next() != '"') throw error("a string key");
      String key = string();
      if (next() != ':') throw error("':'");
      pos++;
      map.put(key, value());
      char c = next();
      pos++;
      if (c == '}') return map;
      if (c != ',') throw error("',' or '}'");
    }
  }

  private List<Object> array() {
    List<Object> list = new ArrayList<>();
    pos++;
    if (next() == ']') {
      pos++;
      return list;
    }
    while (true) {
      list.add(value());
      char c = next();
      pos++;
      if (c == ']') return list;
      if (c != ',') throw error("',' or ']'");
    }
  }

  private String string() {
    StringBuilder s = new StringBuilder();
    pos++;
    while (pos < text.length()) {
      char c = text.charAt(pos++);
      if (c == '"') return s.toString();
      if (c < 0x20) {
        pos--;
        throw error("a control character to be escaped");
      }
      if (c != '\\') {
        s.append(c);
        continue;
      }
      if (pos >= text.length()) break;
      char e = text.charAt(pos++);
      switch (e) {
        case 'b' -> s.append('\b');
        case 'f' -> s.append('\f');
        case 'n' -> s.append('\n');
        case 'r' -> s.append('\r');
        case 't' -> s.append('\t');
        case 'u' -> {
          if (pos + 4 > text.length()) throw error("four hex digits");
          int code = 0;
          for (int end = pos + 4; pos < end; pos++) {
            int digit = Character.digit(text.charAt(pos), 16);
            if (digit < 0) throw error("four hex digits");
            code = code * 16 + digit;
          }
          s.append((char) code);
        }
        case '"', '\\', '/' -> s.append(e);
        default -> {
          pos--;
          throw error("an escape");
        }
      }
    }
    throw error("'\"'");
  }

  /** A number as JSON writes it: -?(0|[1-9][0-9]*)(.[0-9]+)?([eE][+-]?[0-9]+)? */
  private Double number() {
    int start = pos;
    if (at('-')) pos++;
    if (at('0')) {
      pos++;
    } else if (!digits()) {
      throw error(pos == start ? "a value" : "a digit");
    }
    if (at('.')) {
      pos++;
      if (!digits()) throw error("a digit");
    }
    if (at('e') || at('E')) {
      pos++;
      if (at('+') || at('-')) pos++;
      if (!digits()) throw error("a digit");
    }
    return Double.parseDouble(text.substring(start, pos));
  }

  /** Skips digits; whether there was one. */
  private boolean digits() {
    int start = pos;
    while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos++;
    return pos > start;
  }

  private boolean at(char c) {
    return pos < text.length() && text.charAt(pos) == c;
  }

  private Object literal(String word, Object value) {
    pos += word.length();
    return value;
  }

  /** Skips white space and returns the next character, or 0 at the end. */
  private char next() {
    skipSpace();
    return pos < text.length() ? text.charAt(pos) : 0;
  }

  private void skipSpace() {
    while (pos < text.length() && " \t\n\r".indexOf(text.charAt(pos)) >= 0) pos++;
  }

  private IllegalArgumentException error(String expected) {
    return new IllegalArgumentException("Invalid JSON: expected " + expected + " at " + pos);
  }
}
