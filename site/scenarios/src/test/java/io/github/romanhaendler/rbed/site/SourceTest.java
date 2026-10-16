package io.github.romanhaendler.rbed.site;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.romanhaendler.rbed.Rule;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * The Java source each page shows can be copied: filled in at the defaults, and with every
 * option of every choice, it is its imports, then statements that run as the body of a method,
 * then (after a line {@code // In the enclosing class:}) the methods they call. Each compiles
 * against rbed and runs without an exception.
 */
class SourceTest {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)}");
  private static final String HELPERS = "// In the enclosing class:\n";

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

  @SuppressWarnings("unchecked")
  @Test
  void everyScenarioSourceCompilesAndRuns(@TempDir Path out) throws Exception {
    Map<String, String> units = new HashMap<>();
    List<String> atDefaults = new ArrayList<>();
    for (Map<String, Object> s : (List<Map<String, Object>>) Json.parse(Bridge.scenarios())) {
      var parameters = (List<Map<String, Object>>) s.get("parameters");
      Map<String, Double> defaults = new HashMap<>();
      for (var p : parameters) defaults.put((String) p.get("id"), (Double) p.get("default"));
      List<Map<String, Double>> variants = new ArrayList<>(List.of(defaults));
      for (var p : parameters) {
        if (!p.containsKey("options")) continue;
        int options = ((List<?>) p.get("options")).size();
        for (int i = 0; i < options; i++) {
          Map<String, Double> values = new HashMap<>(defaults);
          values.put((String) p.get("id"), (double) i);
          variants.add(values);
        }
      }
      for (int i = 0; i < variants.size(); i++) {
        String source = fill((String) s.get("source"), parameters, variants.get(i));
        assertTrue(source.contains("close()") || source.contains(".detect"),
            s.get("id") + " closes its Detector or runs detect");
        String name = "Source_" + ((String) s.get("id")).replace('-', '_') + "_" + i;
        units.put(name, unit(name, source));
        if (i == 0) atDefaults.add(name);
      }
    }

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

    PrintStream stdout = System.out;
    try (URLClassLoader loader = new URLClassLoader(
        new URL[] {out.toUri().toURL(), rbed.toUri().toURL()}, null)) {
      System.setOut(new PrintStream(new ByteArrayOutputStream()));
      // at the defaults: other options may lead into rbed's rejections on purpose (Validation)
      for (String name : atDefaults) {
        Method main = loader.loadClass(name).getMethod("main", String[].class);
        try {
          main.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException e) {
          throw new AssertionError(name + " throws " + e.getCause(), e.getCause());
        }
      }
    } finally {
      System.setOut(stdout);
    }
  }
}
