import com.sun.source.util.JavacTask;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Pure SYNTAX check: parses sources with javac's parser only (no attribution,
 * no classpath). Missing dependencies therefore produce zero noise; only real
 * syntax errors are reported.
 */
public class ParseCheck {

    public static void main(String[] args) throws Exception {
        List<File> files = new ArrayList<>();
        for (String arg : args) {
            Path p = Paths.get(arg);
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    s.filter(f -> f.toString().endsWith(".java"))
                            .forEach(f -> files.add(f.toFile()));
                }
            } else {
                files.add(p.toFile());
            }
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diags, null, null)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(files);
            JavacTask task = (JavacTask) compiler.getTask(
                    null, fm, diags, List.of("-proc:none", "-nowarn"), null, units);
            task.parse();
        }

        int errors = 0;
        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                errors++;
                System.out.println("  " + (d.getSource() != null ? d.getSource().getName() : "?")
                        + ":" + d.getLineNumber() + " " + d.getMessage(null));
            }
        }
        System.out.println("PARSED_FILES=" + files.size() + " SYNTAX_DIAGS=" + errors);
        if (errors > 0) {
            System.exit(1);
        }
    }
}
