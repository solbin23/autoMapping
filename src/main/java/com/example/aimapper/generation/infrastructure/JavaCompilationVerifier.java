package com.example.aimapper.generation.infrastructure;

import com.example.aimapper.generation.domain.*;
import org.springframework.stereotype.Component;
import javax.tools.*;
import java.net.URI;
import java.util.*;
import java.io.*;

/**
 * 생성한 Java 소스를 실행하거나 어노테이션 프로세서를 로드하지 않고 메모리에서 컴파일 검증한다.
 * 검증 중 생성된 class 파일은 디스크에 남기지 않는다.
 */
@Component
public class JavaCompilationVerifier {
    /** 생성 파일 중 Java 소스를 javac로 검사하고 구조화된 진단 결과를 반환한다. */
    public CompilationResult compile(List<GeneratedFile> files) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return failure("JDK_REQUIRED", "A JDK 21+ with javac is required");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var standard = compiler.getStandardFileManager(diagnostics, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8);
             var manager = new ForwardingJavaFileManager<StandardJavaFileManager>(standard) {
                 @Override public JavaFileObject getJavaFileForOutput(Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                     return new SimpleJavaFileObject(URI.create("mem:///" + name.replace('.', '/') + kind.extension), kind) {
                         @Override public OutputStream openOutputStream() { return new ByteArrayOutputStream(); }
                     };
                 }
             }) {
            var sources = files.stream().filter(f -> f.path().endsWith(".java")).map(f ->
                    new SimpleJavaFileObject(URI.create("string:///" + f.path()), JavaFileObject.Kind.SOURCE) {
                        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return f.content(); }
                    }).toList();
            boolean success = compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "21", "-proc:none", "-encoding", "UTF-8", "-classpath", classpath()),
                    null, sources).call();
            var errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> new CompilationResult.Problem(d.getSource() == null ? null : d.getSource().toUri().getPath().substring(1),
                            d.getLineNumber(), d.getColumnNumber(), d.getCode(), d.getMessage(Locale.ROOT))).toList();
            return new CompilationResult(success, errors);
        } catch (Exception exception) { return failure("COMPILER_FAILURE", exception.getClass().getSimpleName() + ": " + exception.getMessage()); }
    }

    private CompilationResult failure(String code, String message) {
        return new CompilationResult(false, List.of(new CompilationResult.Problem(null, -1, -1, code, message)));
    }

    String classpath() throws java.net.URISyntaxException {
        Set<String> entries = new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))));
        for (ClassLoader loader = Thread.currentThread().getContextClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof java.net.URLClassLoader urls) for (var url : urls.getURLs()) {
                if (url.getProtocol().equals("file")) entries.add(new File(url.toURI()).getPath());
            }
        }
        // The compiler resolves annotations and public method signatures from JUnit classes as well.
        // Add their compile-time dependencies explicitly because application class loaders do not
        // always expose transitive JAR URLs (notably when running as an executable Spring Boot JAR).
        for (var type : List.of(
                org.junit.jupiter.api.Test.class,
                org.junit.jupiter.api.Assertions.class,
                org.apiguardian.api.API.class,
                org.opentest4j.AssertionFailedError.class,
                org.junit.platform.commons.JUnitException.class,
                jakarta.validation.constraints.NotNull.class,
                com.example.aimapper.runtime.MapperRuntime.class)) {
            var url = type.getProtectionDomain().getCodeSource().getLocation();
            if (url.getProtocol().equals("file")) entries.add(new File(url.toURI()).getPath());
        }
        return String.join(File.pathSeparator, entries);
    }
}
