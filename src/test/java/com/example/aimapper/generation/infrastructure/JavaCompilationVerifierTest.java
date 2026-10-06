package com.example.aimapper.generation.infrastructure;

import com.example.aimapper.generation.domain.GeneratedFile;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** 생성 코드의 컴파일 성공과 파일·행·원인이 포함된 실패 진단을 검증한다. */
class JavaCompilationVerifierTest {
    private final JavaCompilationVerifier compiler = new JavaCompilationVerifier();
    @Test void compilesWithoutRunningStaticInitializers() {
        var result = compiler.compile(List.of(new GeneratedFile("example/Safe.java", """
                package example;
                public class Safe {
                    static { if (true) throw new RuntimeException("Never execute uploaded classes"); }
                }
                """)));
        assertThat(result.success()).isTrue();
    }
    @Test void reportsFileLineColumnAndCause() {
        var result = compiler.compile(List.of(new GeneratedFile("example/Broken.java", """
                package example;
                public class Broken {
                    int amount = "invalid";
                }
                """)));
        assertThat(result.success()).isFalse();
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.file()).isEqualTo("example/Broken.java");
            assertThat(error.line()).isEqualTo(3);
            assertThat(error.column()).isPositive();
            assertThat(error.message()).contains("incompatible types");
            assertThat(error.code()).isNotBlank();
        });
    }

    @Test void compilesGeneratedJunitTestWithApiGuardianMetadata() {
        var result = compiler.compile(List.of(new GeneratedFile("example/GeneratedMapperTest.java", """
                package example;
                import org.junit.jupiter.api.Test;
                import static org.junit.jupiter.api.Assertions.assertEquals;
                class GeneratedMapperTest {
                    @Test void mapsValue() { assertEquals(42, 42); }
                }
                """)));

        assertThat(result.success())
                .withFailMessage(result.errors().toString())
                .isTrue();
    }

    @Test void compilerClasspathContainsJunitCompileTimeDependencies() throws Exception {
        var entries = List.of(compiler.classpath().split(java.util.regex.Pattern.quote(File.pathSeparator)));

        assertThat(entries).contains(
                location(org.junit.jupiter.api.Test.class),
                location(org.apiguardian.api.API.class),
                location(org.opentest4j.AssertionFailedError.class),
                location(org.junit.platform.commons.JUnitException.class));
    }

    private String location(Class<?> type) throws Exception {
        return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
    }
}
