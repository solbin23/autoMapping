package com.example.aimapper.generation.application;

import com.example.aimapper.ai.domain.AiMappingSuggestion.Target;
import com.example.aimapper.generation.domain.GeneratedFile;
import com.example.aimapper.generation.domain.GenerationResult;
import com.example.aimapper.generation.domain.MappingDecision;
import com.example.aimapper.generation.infrastructure.JavaCompilationVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.example.aimapper.ai.domain.AiMappingSuggestion.ConversionType.DIRECT;
import static com.example.aimapper.ai.domain.AiMappingSuggestion.ConversionType.FORMAT;
import static com.example.aimapper.ai.domain.AiMappingSuggestion.ConversionType.STRING_PARSE;
import static com.example.aimapper.generation.GenerationFixtures.projects;
import static com.example.aimapper.generation.GenerationFixtures.source;
import static com.example.aimapper.generation.domain.MappingDecision.Status.APPROVED;
import static com.example.aimapper.generation.domain.MappingDecision.Status.REJECTED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 생성된 소스 문자열만 확인하지 않고, Mapper를 컴파일하고 실제 객체를 변환해 결과를 검증한다.
 */
class GeneratedMapperBehaviorTest {

    private static final Logger log = LoggerFactory.getLogger(GeneratedMapperBehaviorTest.class);

    private final MappingProjectService projects = projects();
    private final MapperGenerationService generator =
            new MapperGenerationService(projects, new JavaCompilationVerifier());

    @TempDir
    Path compileDirectory;

    @Test
    void mapsApprovedScalarFieldsAndLeavesRejectedFieldUntouched() throws Exception {
        var project = projects.create(
                List.of(source("LegacyOrder.java", """
                        public class LegacyOrder {
                            private String orderAmount;
                            private String enabled;
                            private long quantity;
                            private String internalMemo;
                        }
                        """)),
                List.of(source("OrderCommand.java", """
                        public class OrderCommand {
                            private int amount;
                            private boolean active;
                            private String quantityText;
                            private String memo;
                        }
                        """)));

        projects.decide(project.id(), 0, approved(
                "LegacyOrder", "orderAmount", "OrderCommand", "amount", STRING_PARSE));
        projects.decide(project.id(), 1, approved(
                "LegacyOrder", "enabled", "OrderCommand", "active", STRING_PARSE));
        projects.decide(project.id(), 2, approved(
                "LegacyOrder", "quantity", "OrderCommand", "quantityText", FORMAT));
        projects.decide(project.id(), 3, new MappingDecision(
                new Target("LegacyOrder", "internalMemo"),
                REJECTED,
                null,
                null));

        GenerationResult generated = generator.generate(
                project.id(), 4, "", "OrderMapper");

        assertThat(generated.compilation().success())
                .withFailMessage(generated.compilation().errors().toString())
                .isTrue();

        try (var classes = compile(generated.files())) {
            Class<?> sourceType = classes.loadClass("LegacyOrder");
            Object legacyOrder = sourceType.getDeclaredConstructor().newInstance();
            writeField(legacyOrder, "orderAmount", "12500");
            writeField(legacyOrder, "enabled", "true");
            writeField(legacyOrder, "quantity", 7L);
            writeField(legacyOrder, "internalMemo", "must not be mapped");

            Object mapper = classes.loadClass("OrderMapper")
                    .getDeclaredConstructor()
                    .newInstance();
            Object command = mapper.getClass()
                    .getMethod("map1", sourceType)
                    .invoke(mapper, legacyOrder);

            Object amount = readField(command, "amount");
            Object active = readField(command, "active");
            Object quantityText = readField(command, "quantityText");
            Object memo = readField(command, "memo");

            log.info(
                    "Scalar mapping result: orderAmount={} -> amount={}, enabled={} -> active={}, "
                            + "quantity={} -> quantityText={}, rejected internalMemo -> memo={}",
                    readField(legacyOrder, "orderAmount"),
                    amount,
                    readField(legacyOrder, "enabled"),
                    active,
                    readField(legacyOrder, "quantity"),
                    quantityText,
                    memo);

            assertThat(amount).isEqualTo(12_500);
            assertThat(active).isEqualTo(true);
            assertThat(quantityText).isEqualTo("7");
            assertThat(memo)
                    .as("REJECTED 필드는 생성 Mapper가 건드리지 않아야 한다")
                    .isNull();

        }
    }

    @Test
    void mapsEveryElementAndPreservesSiblingValuesInNestedCollection() throws Exception {
        var project = projects.create(
                List.of(
                        source("LegacyOrder.java", """
                                import java.util.List;
                                public class LegacyOrder { private List<LegacyLine> lines; }
                                """),
                        source("LegacyLine.java", """
                                public class LegacyLine {
                                    private String productCode;
                                    private String quantity;
                                }
                                """)),
                List.of(
                        source("OrderCommand.java", """
                                import java.util.List;
                                public class OrderCommand { private List<OrderLine> items; }
                                """),
                        source("OrderLine.java", """
                                public class OrderLine {
                                    private String sku;
                                    private int count;
                                }
                                """)));

        projects.decide(project.id(), 0, approved(
                "LegacyOrder", "lines[].productCode", "OrderCommand", "items[].sku", DIRECT));
        projects.decide(project.id(), 1, approved(
                "LegacyOrder", "lines[].quantity", "OrderCommand", "items[].count", STRING_PARSE));

        GenerationResult generated = generator.generate(
                project.id(), 2, "", "OrderLineMapper");

        assertThat(generated.compilation().success())
                .withFailMessage(generated.compilation().errors().toString())
                .isTrue();

        try (var classes = compile(generated.files())) {
            Class<?> lineType = classes.loadClass("LegacyLine");
            Object first = lineType.getDeclaredConstructor().newInstance();
            writeField(first, "productCode", "SKU-001");
            writeField(first, "quantity", "2");
            Object second = lineType.getDeclaredConstructor().newInstance();
            writeField(second, "productCode", "SKU-002");
            writeField(second, "quantity", "5");

            Class<?> orderType = classes.loadClass("LegacyOrder");
            Object order = orderType.getDeclaredConstructor().newInstance();
            writeField(order, "lines", List.of(first, second));

            Object mapper = classes.loadClass("OrderLineMapper")
                    .getDeclaredConstructor()
                    .newInstance();
            Object command = mapper.getClass()
                    .getMethod("map1", orderType)
                    .invoke(mapper, order);

            List<?> items = (List<?>) readField(command, "items");

            for (int index = 0; index < items.size(); index++) {
                Object sourceLine = ((List<?>) readField(order, "lines")).get(index);
                Object targetLine = items.get(index);
                log.info(
                        "Collection mapping result[{}]: productCode={} -> sku={}, quantity={} -> count={}",
                        index,
                        readField(sourceLine, "productCode"),
                        readField(targetLine, "sku"),
                        readField(sourceLine, "quantity"),
                        readField(targetLine, "count"));
            }

            assertThat(items).hasSize(2);
            assertThat(readField(items.get(0), "sku")).isEqualTo("SKU-001");
            assertThat(readField(items.get(0), "count")).isEqualTo(2);
            assertThat(readField(items.get(1), "sku")).isEqualTo("SKU-002");
            assertThat(readField(items.get(1), "count")).isEqualTo(5);

        }
    }

    private MappingDecision approved(
            String sourceClass,
            String sourcePath,
            String targetClass,
            String targetPath,
            com.example.aimapper.ai.domain.AiMappingSuggestion.ConversionType conversionType
    ) {
        return new MappingDecision(
                new Target(sourceClass, sourcePath),
                APPROVED,
                new Target(targetClass, targetPath),
                conversionType);
    }

    private URLClassLoader compile(List<GeneratedFile> generatedFiles) throws Exception {
        List<String> compilerArguments = new ArrayList<>(List.of(
                "--release", "21",
                "-proc:none",
                "-d", compileDirectory.toString(),
                "-classpath", testClasspath()));

        for (GeneratedFile generatedFile : generatedFiles) {
            if (!generatedFile.path().endsWith(".java")) {
                continue;
            }
            Path sourcePath = compileDirectory.resolve(generatedFile.path());
            Files.createDirectories(sourcePath.getParent());
            Files.writeString(sourcePath, generatedFile.content());
            compilerArguments.add(sourcePath.toString());
        }

        int exitCode = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                compilerArguments.toArray(String[]::new));
        assertThat(exitCode).as("생성된 Mapper와 VO가 컴파일되어야 한다").isZero();

        return new URLClassLoader(
                new URL[]{compileDirectory.toUri().toURL()},
                getClass().getClassLoader());
    }

    private String testClasspath() throws Exception {
        Set<String> entries = new LinkedHashSet<>();
        entries.add(System.getProperty("java.class.path"));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (URL url : urls.getURLs()) {
                    if (url.getProtocol().equals("file")) {
                        entries.add(new File(url.toURI()).getPath());
                    }
                }
            }
        }
        entries.add(new File(Test.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath());
        return String.join(File.pathSeparator, entries);
    }

    private void writeField(Object instance, String fieldName, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private Object readField(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }
}
