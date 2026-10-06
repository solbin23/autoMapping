package com.example.aimapper.generation.application;

import com.example.aimapper.generation.domain.*;
import com.example.aimapper.generation.infrastructure.JavaCompilationVerifier;
import com.example.aimapper.schema.application.JavaSourceFile;
import com.example.aimapper.schema.domain.SchemaField;
import com.example.aimapper.schema.domain.SchemaSnapshot;
import com.example.aimapper.ai.domain.AiMappingSuggestion.Target;
import com.example.aimapper.runtime.MapperRuntime;
import com.github.javaparser.StaticJavaParser;
import org.springframework.stereotype.Service;
import javax.lang.model.SourceVersion;
import java.util.*;

/**
 * 승인된 결정만 읽어 Java Mapper와 JUnit 테스트를 만들고 즉시 컴파일 검증한다.
 * 승인하지 않은 추천, 수정 중 결정, 거절 및 미매핑 필드는 생성 코드에 포함하지 않는다.
 */
@Service
public class MapperGenerationService {
    private static final Set<String> NUMERIC_TYPES = Set.of("java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.math.BigDecimal", "java.math.BigInteger");
    private final MappingProjectService projects;
    private final JavaCompilationVerifier compiler;
    public MapperGenerationService(MappingProjectService projects, JavaCompilationVerifier compiler) {
        this.projects = projects; this.compiler = compiler;
    }

    /**
     * 지정 revision의 승인 계획으로 독립 실행 가능한 소스 묶음을 생성한다.
     * 매핑 검증 오류는 파일 대신 위치가 포함된 컴파일 결과로 반환한다.
     */
    public GenerationResult generate(UUID id, long expectedRevision, String packageName, String className) {
        var stored = projects.stored(id);
        var project = stored.project();
        if (project.revision() != expectedRevision) {
            throw new ProjectRevisionConflictException(id, expectedRevision, project.revision());
        }
        if (packageName == null || (!packageName.isEmpty() && !SourceVersion.isName(packageName))
                || className == null || !SourceVersion.isIdentifier(className) || SourceVersion.isKeyword(className)) {
            throw new IllegalArgumentException("Valid Java package and mapper class names are required");
        }
        var approved = project.decisions().stream().filter(d -> d.status() == MappingDecision.Status.APPROVED).toList();
        if (approved.isEmpty()) throw new NoApprovedMappingsException(id);
        List<CompilationResult.Problem> problems = validate(approved, project);
        if (!problems.isEmpty()) return new GenerationResult(id, project.revision(), List.of(), new CompilationResult(false, problems));

        // 소스/대상 클래스 조합마다 하나의 map 메서드를 생성한다.
        Map<String, List<MappingDecision>> groups = new LinkedHashMap<>();
        approved.forEach(d -> groups.computeIfAbsent(d.source().className() + "->" + d.target().className(), key -> new ArrayList<>()).add(d));
        String header = packageName.isEmpty() ? "" : "package " + packageName + ";\n\n";
        String runtimeImport = "import com.example.aimapper.runtime.MapperRuntime;\n";
        StringBuilder mapper = new StringBuilder(header).append(runtimeImport).append("\npublic final class ")
                .append(className).append(" {\n");
        StringBuilder test = new StringBuilder(header).append(runtimeImport)
                .append("import org.junit.jupiter.api.Test;\n")
                .append("import static org.junit.jupiter.api.Assertions.*;\n\nclass ").append(className).append("Test {\n");
        int methodIndex = 0;
        for (var decisions : groups.values()) {
            String source = decisions.getFirst().source().className(), target = decisions.getFirst().target().className();
            String method = "map" + (++methodIndex);
            mapper.append("    public ").append(target).append(' ').append(method).append('(').append(source).append(" source) {\n")
                    .append("        if (source == null) return null;\n        ").append(target)
                    .append(" target = MapperRuntime.instantiate(").append(target).append(".class);\n");
            for (var decision : decisions) mapper.append("        MapperRuntime.writePath(target, \"")
                    .append(decision.target().path()).append("\", MapperRuntime.readPath(source, \"")
                    .append(decision.source().path()).append("\"), \"")
                    .append(decision.conversionType().name()).append("\");\n");
            mapper.append("        return target;\n    }\n\n");
            test.append("    @Test void ").append(method).append("NullSource() { assertNull(new ").append(className).append("().")
                    .append(method).append("(null)); }\n");
            int fieldIndex = 0;
            for (var decision : decisions) {
                var from = field(project.recommendations().asIs(), decision.source());
                var to = field(project.recommendations().toBe(), decision.target());
                String input = sample(from.javaType(), to.javaType());
                String expected = sample(to.javaType(), from.javaType());
                if (input == null || expected == null) continue;
                if (decision.conversionType().name().equals("FORMAT") && to.javaType().equals("java.lang.String")) expected = "String.valueOf(" + input + ")";
                if (decision.conversionType().name().equals("NUMERIC_CONVERSION") && to.javaType().equals("java.math.BigDecimal")) {
                    expected = "new java.math.BigDecimal(String.valueOf(" + input + "))";
                }
                expected = wrapLists(expected, decision.target().path());
                test.append("    @Test void ").append(method).append("Field").append(++fieldIndex)
                        .append("() {\n        var source = MapperRuntime.instantiate(").append(source).append(".class);\n");
                for (var setup : decisions) {
                    String value = sample(field(project.recommendations().asIs(), setup.source()).javaType(), field(project.recommendations().toBe(), setup.target()).javaType());
                    if (value != null) test.append("        MapperRuntime.writePath(source, \"").append(setup.source().path())
                            .append("\", ").append(wrapLists(value, setup.source().path())).append(", \"DIRECT\");\n");
                }
                test.append("        var target = new ").append(className).append("().").append(method).append("(source);\n")
                        .append("        assertEquals(").append(expected).append(", MapperRuntime.readPath(target, \"")
                        .append(decision.target().path()).append("\"));\n    }\n");
            }
        }
        test.append("    @Test void rejectsInvalidConversion() {\n        assertThrows(IllegalArgumentException.class, () -> ")
                .append("MapperRuntime.convert(\"not-a-number\", int.class, \"STRING_PARSE\"));\n    }\n")
                .append("    @Test void rejectsOverflow() {\n        assertThrows(ArithmeticException.class, () -> ")
                .append("MapperRuntime.convert(\"2147483648\", int.class, \"STRING_PARSE\"));\n    }\n}\n");
        mapper.append("}\n");

        String prefix = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
        List<GeneratedFile> files = new ArrayList<>();
        files.add(new GeneratedFile("src/main/java/" + prefix + className + ".java", mapper.toString()));
        files.add(new GeneratedFile("src/test/java/" + prefix + className + "Test.java", test.toString()));
        stored.asIs().forEach(f -> files.add(sourceFile(f, "as-is")));
        stored.toBe().forEach(f -> files.add(sourceFile(f, "to-be")));
        if (files.stream().map(GeneratedFile::path).distinct().count() != files.size()) throw new IllegalArgumentException("Duplicate source file paths");
        files.add(new GeneratedFile("build.gradle", """
                plugins { id 'java' }
                repositories { mavenLocal(); mavenCentral() }
                java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
                sourceSets.main.java.srcDirs = ['src/main/java', 'sources/as-is', 'sources/to-be']
                dependencies {
                    implementation '%s'
                    compileOnly 'jakarta.validation:jakarta.validation-api:3.0.2'
                    testImplementation platform('org.junit:junit-bom:5.12.2')
                    testImplementation 'org.junit.jupiter:junit-jupiter'
                    testImplementation 'org.apiguardian:apiguardian-api:1.1.2'
                    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
                }
                test { useJUnitPlatform() }
                """.formatted(MapperRuntime.MAVEN_COORDINATE)));
        files.add(new GeneratedFile("README.txt", "Java 21 and Gradle are required. Run: gradle test\n"
                + "Only explicitly APPROVED decisions are mapped. See compilation-report.json in the ZIP.\n"
                + "The mapper depends on " + MapperRuntime.MAVEN_COORDINATE + ". Publish it to your artifact repository or Maven Local.\n"
                + "VOs need usable no-arg constructors and writable fields; reflection must be allowed.\n"
                + "Add external VO dependencies to build.gradle as needed.\n"));
        // 반환 전에 사용자 VO, Mapper와 JUnit을 한 번에 컴파일해 오류 위치를 확정한다.
        return new GenerationResult(id, project.revision(), files, compiler.compile(files));
    }

    /** 승인 계획에서 중복 대상, 컬렉션 깊이 및 지원하지 않는 타입 변환을 검사한다. */
    private List<CompilationResult.Problem> validate(List<MappingDecision> approved, MappingProject project) {
        List<CompilationResult.Problem> errors = new ArrayList<>();
        Set<String> targets = new HashSet<>();
        for (var d : approved) {
            String key = d.source().className() + "->" + d.target().className() + ":" + d.target().path();
            String message = null;
            if (!targets.add(key)) message = "Multiple approved sources write the same target";
            if (Set.of("UNKNOWN", "CUSTOM").contains(d.conversionType().name())) message = "An implemented conversion type must be approved";
            if (collectionDepth(d.source().path()) != collectionDepth(d.target().path())) message = "Collection depth changes require an explicit custom mapper";
            var from = field(project.recommendations().asIs(), d.source());
            var to = field(project.recommendations().toBe(), d.target());
            if (d.conversionType().name().equals("DIRECT") && !boxed(from.javaType()).equals(boxed(to.javaType()))) message = "DIRECT requires matching Java types; approve a conversion";
            String sourceType = boxed(from.javaType()), targetType = boxed(to.javaType());
            boolean supported = switch (d.conversionType()) {
                case DIRECT -> sourceType.equals(targetType);
                case NUMERIC_CONVERSION -> NUMERIC_TYPES.contains(sourceType) && NUMERIC_TYPES.contains(targetType);
                case STRING_PARSE -> sourceType.equals("java.lang.String") && (NUMERIC_TYPES.contains(targetType)
                        || Set.of("java.lang.Boolean", "java.lang.Character").contains(targetType));
                case FORMAT -> targetType.equals("java.lang.String") && (NUMERIC_TYPES.contains(sourceType)
                        || Set.of("java.lang.String", "java.lang.Boolean", "java.lang.Character").contains(sourceType));
                case COLLECTION_MAPPING -> from.collection() && to.collection() && sourceType.equals(targetType);
                default -> false;
            };
            if (!supported) message = "Unsupported approved conversion: " + from.javaType() + " -> " + to.javaType() + " (" + d.conversionType() + ")";
            if (message != null) errors.add(new CompilationResult.Problem(d.target().className() + ":" + d.target().path(), -1, -1, "MAPPING_VALIDATION", message));
        }
        return errors;
    }

    private SchemaField field(SchemaSnapshot snapshot, Target target) {
        return snapshot.classes().stream().filter(c -> c.qualifiedName().equals(target.className())).flatMap(c -> c.fields().stream())
                .filter(f -> f.path().equals(target.path())).findFirst().orElseThrow();
    }

    private GeneratedFile sourceFile(JavaSourceFile source, String side) {
        var unit = StaticJavaParser.parse(source.content());
        String prefix = unit.getPackageDeclaration().map(p -> p.getNameAsString().replace('.', '/') + "/").orElse("");
        String name = source.fileName().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (!name.matches("[\\p{L}\\p{N}_$-]+\\.java")) throw new IllegalArgumentException("Invalid Java source filename");
        return new GeneratedFile("sources/" + side + "/" + prefix + name, source.content());
    }
    private int collectionDepth(String path) { return (path.length() - path.replace("[]", "").length()) / 2; }
    private String wrapLists(String expression, String path) {
        for (int i = 0; i < collectionDepth(path); i++) expression = "java.util.List.of(" + expression + ")";
        return expression;
    }
    private String boxed(String type) {
        return switch (type) {
            case "int" -> "java.lang.Integer"; case "long" -> "java.lang.Long"; case "short" -> "java.lang.Short";
            case "byte" -> "java.lang.Byte"; case "float" -> "java.lang.Float"; case "double" -> "java.lang.Double";
            case "boolean" -> "java.lang.Boolean"; case "char" -> "java.lang.Character"; default -> type;
        };
    }
    private String sample(String type, String other) {
        return switch (boxed(type)) {
            case "java.lang.String" -> boxed(other).equals("java.lang.Boolean") ? "\"true\"" : boxed(other).equals("java.lang.Character") ? "\"4\"" : "\"42\"";
            case "java.lang.Integer" -> "42"; case "java.lang.Long" -> "42L"; case "java.lang.Short" -> "(short)42";
            case "java.lang.Byte" -> "(byte)42"; case "java.lang.Float" -> "42.0f"; case "java.lang.Double" -> "42.0d";
            case "java.lang.Boolean" -> "true"; case "java.lang.Character" -> "'4'";
            case "java.math.BigDecimal" -> "new java.math.BigDecimal(\"42\")";
            case "java.math.BigInteger" -> "new java.math.BigInteger(\"42\")";
            default -> null;
        };
    }
}
