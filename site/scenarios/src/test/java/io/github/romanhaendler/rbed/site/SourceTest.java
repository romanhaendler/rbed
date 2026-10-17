package io.github.romanhaendler.rbed.site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Rule;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Java source each page shows can be copied and does what the page shows: filled in at the
 * defaults, and with every option of every choice, it is its imports, then statements that run
 * as the body of a method, then (after a line {@code // In the enclosing class:}) the methods
 * they call. Each compiles against rbed, and at the defaults it runs without an exception and
 * its Detectors report exactly the Events the bridge reports. Each threshold and window the
 * chart draws is one the source compares with or opens.
 */
class SourceTest {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");
  private static final String HELPERS = "// In the enclosing class:\n";

  /**
   * A listener the test compiles next to the sources: {@link #teed} puts it around each
   * Detector's own listener, and it records the Ended Events as BridgeTest's {@code events} writes
   * them.
   */
  private static final String TEE = """
      import io.github.romanhaendler.rbed.Event;
      import io.github.romanhaendler.rbed.EventListener;
      import java.util.ArrayList;
      import java.util.List;

      public class Tee implements EventListener {
        public static final List<String> ended = new ArrayList<>();
        private final EventListener inner;

        private Tee(EventListener inner) {
          this.inner = inner;
        }

        public static EventListener tee(EventListener inner) {
          return new Tee(inner);
        }

        public void onPending(Event e) {
          if (inner != null) inner.onPending(e);
        }

        public void onConfirmed(Event e) {
          if (inner != null) inner.onConfirmed(e);
        }

        public void onDiscarded(Event e) {
          if (inner != null) inner.onDiscarded(e);
        }

        public void onEnded(Event e) {
          ended.add(e.name() + " " + e.start() + ".." + e.end().getAsLong()
              + (e.truncatedStart() ? " truncatedStart" : "")
              + (e.truncatedEnd() ? " truncatedEnd" : ""));
          if (inner != null) inner.onEnded(e);
        }
      }
      """;

  /** A source filled in as site/src/parameters.ts fillSource does. */
  @SuppressWarnings("unchecked")
  static String fill(String template, List<Map<String, Object>> parameters,
      Map<String, Double> values) {
    Matcher m = PLACEHOLDER.matcher(template);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String id = m.group(1);
      Map<String, Object> p = parameters.stream().filter(q -> q.get("id").equals(id))
          .findFirst().orElseThrow(() -> new AssertionError("No parameter " + id));
      double value = values.get(id);
      String text;
      if (p.containsKey("options")) {
        var option = ((List<Map<String, Object>>) p.get("options")).get((int) value);
        text = fill((String) option.get("source"), parameters, values);
      } else {
        text = value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
      }
      m.appendReplacement(out, Matcher.quoteReplacement(text));
    }
    return m.appendTail(out).toString();
  }

  /** The compilation unit for a filled source, as class {@code name}. */
  static String unit(String name, String source) {
    StringBuilder imports = new StringBuilder();
    String rest = source;
    while (rest.startsWith("import ") || rest.startsWith("\n")) {
      int eol = rest.indexOf('\n');
      imports.append(rest, 0, eol + 1);
      rest = rest.substring(eol + 1);
    }
    int split = rest.indexOf(HELPERS);
    String body = split < 0 ? rest : rest.substring(0, split);
    String helpers = split < 0 ? "" : rest.substring(split + HELPERS.length());
    return imports + "public class " + name + " {\n"
        + "public static void main(String[] args) throws Exception {\n" + body + "}\n"
        + helpers + "}\n";
  }

  /**
   * The source with each Detector's listener wrapped in {@code Tee.tee(...)}, or
   * {@code .listener(Tee.tee(null))} added before {@code .build()} where it has none.
   */
  static String teed(String source) {
    StringBuilder out = new StringBuilder();
    int from = 0;
    for (int build; (build = source.indexOf(".build()", from)) >= 0; ) {
      int listener = source.indexOf(".listener(", from);
      if (listener >= 0 && listener < build) {
        out.append(source, from, listener).append(".listener(Tee.tee(")
            .append(source, listener + ".listener(".length(), build).append(')');
      } else {
        out.append(source, from, build).append(".listener(Tee.tee(null))");
      }
      out.append(".build()");
      from = build + ".build()".length();
    }
    return out.append(source.substring(from)).toString();
  }

  /** A Scenario of the catalogue with its parameter values: the defaults, then each option. */
  record Variant(String id, String source, Map<String, Double> values) {
    String parameters() {
      StringBuilder json = new StringBuilder("{");
      for (var v : values.entrySet()) {
        if (json.length() > 1) json.append(',');
        Json.number(Json.string(json, v.getKey()).append(':'), v.getValue());
      }
      return json.append('}').toString();
    }
  }

  /** Every Scenario's variants, its defaults first. */
  @SuppressWarnings("unchecked")
  static List<List<Variant>> variants() {
    List<List<Variant>> all = new ArrayList<>();
    for (Map<String, Object> s : (List<Map<String, Object>>) Json.parse(Bridge.scenarios())) {
      var parameters = (List<Map<String, Object>>) s.get("parameters");
      Map<String, Double> defaults = new HashMap<>();
      for (var p : parameters) defaults.put((String) p.get("id"), (Double) p.get("default"));
      List<Map<String, Double>> values = new ArrayList<>(List.of(defaults));
      for (var p : parameters) {
        if (!p.containsKey("options")) continue;
        int options = ((List<?>) p.get("options")).size();
        for (int i = 0; i < options; i++) {
          Map<String, Double> option = new HashMap<>(defaults);
          option.put((String) p.get("id"), (double) i);
          values.add(option);
        }
      }
      String id = (String) s.get("id");
      all.add(values.stream()
          .map(v -> new Variant(id, fill((String) s.get("source"), parameters, v), v)).toList());
    }
    return all;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> run(Variant v) {
    return (Map<String, Object>) Json.parse(Bridge.run(v.id(), v.parameters(), "{}"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void everyScenarioSourceCompilesRunsAndFindsTheBridgesEvents(@TempDir Path out)
      throws Exception {
    Map<String, String> units = new HashMap<>(Map.of("Tee", TEE));
    Map<String, List<String>> atDefaults = new HashMap<>();
    for (List<Variant> variants : variants()) {
      for (int i = 0; i < variants.size(); i++) {
        Variant v = variants.get(i);
        assertTrue(v.source().contains("close()") || v.source().contains(".detect"),
            v.id() + " closes its Detector or runs detect");
        String name = "Source_" + v.id().replace('-', '_') + "_" + i;
        units.put(name, unit(name, teed(v.source())));
        if (i == 0) atDefaults.put(name, BridgeTest.events(run(v)));
      }
    }

    PrintStream stdout = System.out;
    try (URLClassLoader loader = compile(units, out)) {
      List<String> ended = (List<String>) loader.loadClass("Tee").getField("ended").get(null);
      System.setOut(new PrintStream(new ByteArrayOutputStream()));
      // at the defaults: other options may lead into rbed's rejections on purpose (Validation)
      for (var expected : atDefaults.entrySet()) {
        String name = expected.getKey();
        Method main = loader.loadClass(name).getMethod("main", String[].class);
        ended.clear();
        try {
          main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException e) {
          throw new AssertionError(name + " throws " + e.getCause(), e.getCause());
        }
        assertEquals(expected.getValue(), ended, name + " finds the bridge's Events");
      }
    } finally {
      System.setOut(stdout);
    }
  }

  /**
   * The snippet under "Read an Expression's value" on the Conditions and Expressions page is a
   * source like a Scenario's: it compiles, runs and prints what its comment says.
   */
  @Test
  void theReadAnExpressionsValueSnippetCompilesAndPrintsTheValues(@TempDir Path out)
      throws Exception {
    String page = Files.readString(Path.of("..", "src", "content", "conditions-and-expressions.md"));
    int start = page.indexOf("```java\n", page.indexOf("## Read an Expression's value"))
        + "```java\n".length();
    String snippet = page.substring(start, page.indexOf("```", start));
    PrintStream stdout = System.out;
    ByteArrayOutputStream printed = new ByteArrayOutputStream();
    try (URLClassLoader loader = compile(Map.of("Snippet", unit("Snippet", snippet)), out)) {
      Method main = loader.loadClass("Snippet").getMethod("main", String[].class);
      System.setOut(new PrintStream(printed));
      main.invoke(null, (Object) new String[0]);
    } finally {
      System.setOut(stdout);
    }
    assertEquals(List.of("OptionalDouble.empty", "OptionalDouble.empty", "OptionalDouble[25.0]",
        "OptionalDouble[26.0]"), printed.toString().lines().toList());
  }

  /** Compiles the units against rbed into {@code out} and loads them with rbed alone. */
  static URLClassLoader compile(Map<String, String> units, Path out) throws Exception {
    List<JavaFileObject> files = new ArrayList<>();
    for (var unit : units.entrySet()) {
      files.add(new SimpleJavaFileObject(URI.create("string:///" + unit.getKey() + ".java"),
          JavaFileObject.Kind.SOURCE) {
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
          return unit.getValue();
        }
      });
    }
    Path rbed = Path.of(Rule.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean compiled = compiler.getTask(null, null, diagnostics,
        List.of("-classpath", rbed.toString(), "-d", out.toString(), "-proc:none"), null, files)
        .call();
    StringBuilder errors = new StringBuilder();
    for (var d : diagnostics.getDiagnostics()) {
      errors.append(d.getSource() == null ? "" : d.getSource().getName()).append(':')
          .append(d.getLineNumber()).append(": ").append(d.getMessage(null)).append('\n');
    }
    assertTrue(compiled, errors.toString());
    return new URLClassLoader(new URL[] {out.toUri().toURL(), rbed.toUri().toURL()}, null);
  }

  @SuppressWarnings("unchecked")
  @Test
  void everyDrawnThresholdAndWindowIsOneOfTheSource() throws Exception {
    Pattern rule = Pattern.compile("Rule (\\w+) = Rule\\.named\\(\"(\\w+)\"\\)");
    Pattern within = Pattern.compile("after\\((\\w+)\\)\\.within\\(Duration\\.of(\\w+)\\(([\\d_]+)\\)\\)");
    for (List<Variant> variants : variants()) {
      for (Variant v : variants) {
        Map<String, Object> result = run(v);
        if (result.containsKey("error")) continue;
        for (var t : (List<Map<String, Object>>) result.get("thresholds")) {
          Set<Double> compared = comparedWith(v.source(), (String) t.get("channel"), result);
          assertTrue(compared.contains((Double) t.get("value")), v.id() + " " + v.values()
              + ": " + t + " is none of the values the source compares with: " + compared);
        }
        Map<String, String> names = new HashMap<>();
        for (Matcher m = rule.matcher(v.source()); m.find(); ) names.put(m.group(1), m.group(2));
        Set<String> opened = new HashSet<>();
        for (Matcher m = within.matcher(v.source()); m.find(); ) {
          Duration d = (Duration) Duration.class.getMethod("of" + m.group(2), long.class)
              .invoke(null, Long.parseLong(m.group(3).replace("_", "")));
          opened.add(names.get(m.group(1)) + " " + d.toMillis());
        }
        Set<String> drawn = new HashSet<>();
        for (var w : (List<Map<String, Object>>) result.get("windows")) {
          drawn.add(w.get("after") + " " + ((Double) w.get("millis")).longValue());
        }
        assertEquals(opened, drawn, v.id() + " " + v.values() + ": windows");
      }
    }
  }

  /**
   * The values the source compares {@code channel} with ({@code channel.above(500)},
   * {@code .below(0.5)}); where it compares with another Channel, every value of that Channel.
   */
  @SuppressWarnings("unchecked")
  static Set<Double> comparedWith(String source, String channel, Map<String, Object> result) {
    Set<Double> values = new HashSet<>();
    Matcher m = Pattern.compile("\\b" + channel + "\\.(?:above|below)\\(([\\w.]+)\\)")
        .matcher(source);
    while (m.find()) {
      String argument = m.group(1).replace("_", "");
      if (Character.isDigit(argument.charAt(0))) {
        values.add(Double.parseDouble(argument));
        continue;
      }
      for (var sample : (List<Map<String, Object>>) result.get("samples")) {
        Object value = ((Map<String, Object>) sample.get("values")).get(argument);
        if (value != null) values.add((Double) value);
      }
    }
    return values;
  }
}
