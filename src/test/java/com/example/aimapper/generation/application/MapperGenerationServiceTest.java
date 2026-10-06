package com.example.aimapper.generation.application;

import com.example.aimapper.ai.domain.AiMappingSuggestion.Target;
import com.example.aimapper.generation.domain.*;
import com.example.aimapper.generation.infrastructure.JavaCompilationVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.io.File;
import java.util.*;
import javax.tools.ToolProvider;
import static com.example.aimapper.generation.GenerationFixtures.*;
import static com.example.aimapper.ai.domain.AiMappingSuggestion.ConversionType.*;
import static com.example.aimapper.generation.domain.MappingDecision.Status.*;
import static org.assertj.core.api.Assertions.*;

/** 승인 매핑만으로 Mapper·JUnit을 만들고 잘못된 변환을 거부하는지 검증한다. */
class MapperGenerationServiceTest {
    private final MappingProjectService projects = projects();
    private final MapperGenerationService generator = new MapperGenerationService(projects, new JavaCompilationVerifier());
    @TempDir Path temp;

    @Test void onlyApprovedMappingsAreGeneratedAndGeneratedJUnitTestsExecute() throws Exception {
        var project = projects.create(List.of(source("Old.java", "public class Old { private String amount; private String secret; private String ignored; private String untouched; }")),
                List.of(source("New.java", "public class New { private int amount; private String secret; private String ignored; private String untouched; }")));
        projects.decide(project.id(), 0, new MappingDecision(new Target("Old", "amount"), APPROVED, new Target("New", "amount"), STRING_PARSE));
        projects.decide(project.id(), 1, new MappingDecision(new Target("Old", "secret"), MODIFIED, new Target("New", "secret"), DIRECT));
        projects.decide(project.id(), 2, new MappingDecision(new Target("Old", "ignored"), REJECTED, null, null));
        var result = generator.generate(project.id(), 3, "", "ApprovedMapper");
        assertThat(result.compilation().success()).withFailMessage(result.compilation().errors().toString()).isTrue();
        assertThat(result.files().getFirst().content()).contains("readPath(source, \"amount\")").doesNotContain("\"secret\"", "\"ignored\"", "\"untouched\"");
        executeGeneratedTests(result, "ApprovedMapperTest");
    }

    @Test void nestedCollectionsAndSiblingMappingsExecute() throws Exception {
        var project = projects.create(List.of(
                source("Old.java", "import java.util.List; public class Old { private List<OldItem> items; }"),
                source("OldItem.java", "public class OldItem { private String amount; private String label; }")), List.of(
                source("New.java", "import java.util.List; public class New { private List<NewItem> entries; }"),
                source("NewItem.java", "public class NewItem { private long amount; private String label; }")));
        projects.decide(project.id(), 0, new MappingDecision(new Target("Old", "items[].amount"), APPROVED, new Target("New", "entries[].amount"), STRING_PARSE));
        projects.decide(project.id(), 1, new MappingDecision(new Target("Old", "items[].label"), APPROVED, new Target("New", "entries[].label"), DIRECT));
        var result = generator.generate(project.id(), 2, "", "NestedMapper");
        assertThat(result.compilation().success()).withFailMessage(result.compilation().errors().toString()).isTrue();
        executeGeneratedTests(result, "NestedMapperTest");
    }

    @Test void modifiedMappingsRequireApprovalAndGenerationRequiresCurrentRevision() {
        var project = projects.create(List.of(source("Old.java", "class Old { int amount; }")), List.of(source("New.java", "class New { int amount; }")));
        projects.decide(project.id(), 0, new MappingDecision(new Target("Old", "amount"), MODIFIED, new Target("New", "amount"), DIRECT));
        assertThatThrownBy(() -> generator.generate(project.id(), 1, "", "Mapper")).hasMessageContaining("approved");
        projects.decide(project.id(), 1, new MappingDecision(new Target("Old", "amount"), APPROVED, new Target("New", "amount"), DIRECT));
        assertThatThrownBy(() -> generator.generate(project.id(), 1, "", "Mapper")).hasMessageContaining("revision");
        assertThatThrownBy(() -> generator.generate(project.id(), 2, "../escape", "Mapper")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void numericAndStringFormattingGeneratedTestsExecute() throws Exception {
        var project = projects.create(List.of(source("Old.java", "public class Old { private double amount; private double price; private String active; private String initial; }")),
                List.of(source("New.java", "public class New { private java.math.BigDecimal amount; private String price; private boolean active; private char initial; }")));
        projects.decide(project.id(), 0, new MappingDecision(new Target("Old", "amount"), APPROVED, new Target("New", "amount"), NUMERIC_CONVERSION));
        projects.decide(project.id(), 1, new MappingDecision(new Target("Old", "price"), APPROVED, new Target("New", "price"), FORMAT));
        projects.decide(project.id(), 2, new MappingDecision(new Target("Old", "active"), APPROVED, new Target("New", "active"), STRING_PARSE));
        projects.decide(project.id(), 3, new MappingDecision(new Target("Old", "initial"), APPROVED, new Target("New", "initial"), STRING_PARSE));
        var result = generator.generate(project.id(), 4, "", "ConversionMapper");
        assertThat(result.compilation().success()).withFailMessage(result.compilation().errors().toString()).isTrue();
        executeGeneratedTests(result, "ConversionMapperTest");
    }

    @Test void duplicateTargetsAndUnsupportedConversionsProduceDiagnostics() {
        var project = projects.create(List.of(source("Old.java", "class Old { int amount; int other; }")), List.of(source("New.java", "class New { int amount; }")));
        projects.decide(project.id(), 0, new MappingDecision(new Target("Old", "amount"), APPROVED, new Target("New", "amount"), CUSTOM));
        projects.decide(project.id(), 1, new MappingDecision(new Target("Old", "other"), APPROVED, new Target("New", "amount"), DIRECT));
        var result = generator.generate(project.id(), 2, "", "Mapper");
        assertThat(result.compilation().success()).isFalse();
        assertThat(result.compilation().errors()).hasSize(2).allSatisfy(e -> assertThat(e.code()).isEqualTo("MAPPING_VALIDATION"));
    }

    private void executeGeneratedTests(GenerationResult result, String testClass) throws Exception {
        List<String> args = new ArrayList<>(List.of("--release", "21", "-proc:none", "-d", temp.toString(), "-classpath", testClasspath()));
        for (var file : result.files()) {
            if (!file.path().endsWith(".java")) continue;
            Path path = temp.resolve(file.path());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.content());
            args.add(path.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new))).isZero();
        try (var loader = new URLClassLoader(new URL[]{temp.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> generated = loader.loadClass(testClass);
            var constructor = generated.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            int executed = 0;
            for (var method : generated.getDeclaredMethods()) if (method.isAnnotationPresent(Test.class)) {
                method.setAccessible(true);
                try { method.invoke(instance); }
                catch (java.lang.reflect.InvocationTargetException ex) { throw new AssertionError("Generated test failed: " + method.getName(), ex.getCause()); }
                executed++;
            }
            assertThat(executed).isGreaterThanOrEqualTo(4);
            var mapperClass = loader.loadClass(testClass.substring(0, testClass.length() - 4));
            var convert = mapperClass.getDeclaredMethod("convert", Object.class, Class.class, String.class);
            convert.setAccessible(true);
            assertThat(convert.invoke(null, "true", boolean.class, "STRING_PARSE")).isEqualTo(true);
            assertThat(convert.invoke(null, "x", char.class, "STRING_PARSE")).isEqualTo('x');
            assertThat(convert.invoke(null, 42, long.class, "NUMERIC_CONVERSION")).isEqualTo(42L);
            assertThat(convert.invoke(null, 42, String.class, "FORMAT")).isEqualTo("42");
            assertThatThrownBy(() -> convert.invoke(null, "maybe", boolean.class, "STRING_PARSE"))
                    .isInstanceOf(java.lang.reflect.InvocationTargetException.class).hasCauseInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> convert.invoke(null, null, int.class, "DIRECT"))
                    .isInstanceOf(java.lang.reflect.InvocationTargetException.class).hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    private String testClasspath() throws Exception {
        Set<String> entries = new LinkedHashSet<>();
        entries.add(System.getProperty("java.class.path"));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) for (URL url : urls.getURLs()) if (url.getProtocol().equals("file")) entries.add(new File(url.toURI()).getPath());
        }
        entries.add(new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath());
        return String.join(File.pathSeparator, entries);
    }
}
