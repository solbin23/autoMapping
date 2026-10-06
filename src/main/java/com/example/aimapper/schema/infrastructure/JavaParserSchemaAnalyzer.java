package com.example.aimapper.schema.infrastructure;

import com.example.aimapper.schema.application.JavaSourceFile;
import com.example.aimapper.schema.application.SchemaAnalysisPort;
import com.example.aimapper.schema.domain.SchemaClass;
import com.example.aimapper.schema.domain.SchemaField;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JavaParser로 소스의 클래스, 중첩 필드, 타입, 주석과 어노테이션을 추출한다.
 * 실행하거나 컴파일하지 않고 구문 트리만 읽으므로 업로드 코드의 동작에는 의존하지 않는다.
 */
@Component
public class JavaParserSchemaAnalyzer implements SchemaAnalysisPort {

    private static final Set<String> COLLECTION_TYPES = Set.of("List", "Collection", "Set", "Iterable");
    private static final Set<String> NON_NULL_ANNOTATIONS = Set.of("NotNull", "NonNull", "Nonnull");
    private static final int MAX_NESTING_DEPTH = 20;

    private final JavaParser javaParser;

    public JavaParserSchemaAnalyzer() {
        var typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        var configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        this.javaParser = new JavaParser(configuration);
    }

    /** 여러 Java 파일을 함께 분석해 타입 참조와 중첩 구조를 연결한 스키마를 만든다. */
    @Override
    public List<SchemaClass> analyze(List<JavaSourceFile> sourceFiles) {
        List<ParsedClass> parsedClasses = parse(sourceFiles);
        Map<String, ParsedClass> classIndex = index(parsedClasses);

        return parsedClasses.stream()
                .map(parsed -> new SchemaClass(
                        parsed.packageName(),
                        parsed.declaration().getNameAsString(),
                        parsed.qualifiedName(),
                        flatten(parsed, classIndex)))
                .toList();
    }

    private List<ParsedClass> parse(List<JavaSourceFile> sourceFiles) {
        List<ParsedClass> classes = new ArrayList<>();
        for (JavaSourceFile sourceFile : sourceFiles) {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceFile.content());
            parseResult.getProblems().stream().findFirst().ifPresent(problem -> {
                throw new SchemaParseException(sourceFile.fileName(), problem.getVerboseMessage());
            });
            CompilationUnit unit = parseResult.getResult()
                    .orElseThrow(() -> new SchemaParseException(sourceFile.fileName(), "No compilation unit found"));

            String packageName = unit.getPackageDeclaration()
                    .map(declaration -> declaration.getNameAsString())
                    .orElse("");
            Map<String, String> imports = unit.getImports().stream()
                    .filter(importDeclaration -> !importDeclaration.isAsterisk())
                    .collect(Collectors.toMap(
                            importDeclaration -> importDeclaration.getName().getIdentifier(),
                            importDeclaration -> importDeclaration.getNameAsString(),
                            (left, right) -> left));

            unit.getTypes().stream()
                    .filter(TypeDeclaration::isClassOrInterfaceDeclaration)
                    .map(TypeDeclaration::asClassOrInterfaceDeclaration)
                    .filter(declaration -> !declaration.isInterface())
                    .forEach(declaration -> classes.add(new ParsedClass(packageName, imports, declaration)));
        }
        if (classes.isEmpty()) {
            throw new SchemaParseException("uploaded sources", "No class declaration found");
        }
        return classes;
    }

    private Map<String, ParsedClass> index(List<ParsedClass> classes) {
        Map<String, ParsedClass> index = new HashMap<>();
        for (ParsedClass parsedClass : classes) {
            index.put(parsedClass.declaration().getNameAsString(), parsedClass);
            index.put(parsedClass.qualifiedName(), parsedClass);
        }
        return index;
    }

    private List<SchemaField> flatten(ParsedClass root, Map<String, ParsedClass> classIndex) {
        List<SchemaField> result = new ArrayList<>();
        flattenFields(root, "", false, classIndex, new HashSet<>(), 0, result);
        return result;
    }

    private void flattenFields(
            ParsedClass current,
            String parentPath,
            boolean parentCollection,
            Map<String, ParsedClass> classIndex,
            Set<String> visiting,
            int depth,
            List<SchemaField> result
    ) {
        if (depth > MAX_NESTING_DEPTH || !visiting.add(current.qualifiedName())) {
            return;
        }

        for (FieldDeclaration field : current.declaration().getFields()) {
            if (field.isStatic()) {
                continue;
            }
            field.getVariables().forEach(variable -> {
                Type type = variable.getType();
                TypeShape shape = typeShape(type, current);
                String segment = variable.getNameAsString() + (shape.collection() ? "[]" : "");
                String path = parentPath.isEmpty() ? segment : parentPath + "." + segment;
                boolean collection = parentCollection || shape.collection();
                ParsedClass nested = classIndex.get(shape.elementTypeName());
                if (nested == null) {
                    nested = classIndex.get(simpleName(shape.elementTypeName()));
                }

                if (nested != null && !visiting.contains(nested.qualifiedName())) {
                    flattenFields(nested, path, collection, classIndex, visiting, depth + 1, result);
                } else {
                    result.add(new SchemaField(
                            path,
                            variable.getNameAsString(),
                            shape.javaType(),
                            collection,
                            isNullable(type, field),
                            description(field),
                            field.getAnnotations().stream().map(Object::toString).toList()));
                }
            });
        }
        visiting.remove(current.qualifiedName());
    }

    private TypeShape typeShape(Type type, ParsedClass owner) {
        if (type instanceof ArrayType arrayType) {
            String element = canonicalType(arrayType.getComponentType(), owner);
            return new TypeShape(element, element, true);
        }
        if (type instanceof ClassOrInterfaceType classType
                && COLLECTION_TYPES.contains(classType.getName().getIdentifier())) {
            Type elementType = classType.getTypeArguments()
                    .filter(arguments -> !arguments.isEmpty())
                    .map(arguments -> arguments.get(0))
                    .orElse(null);
            String element = elementType == null ? "java.lang.Object" : canonicalType(elementType, owner);
            return new TypeShape(element, element, true);
        }
        String canonical = canonicalType(type, owner);
        return new TypeShape(canonical, canonical, false);
    }

    private String canonicalType(Type type, ParsedClass owner) {
        if (type instanceof PrimitiveType) {
            return type.asString();
        }
        try {
            ResolvedType resolvedType = type.resolve();
            return resolvedType.describe();
        } catch (RuntimeException ignored) {
            String rawName = type.asString();
            String simpleName = simpleName(rawName);
            if (owner.imports().containsKey(simpleName)) {
                return owner.imports().get(simpleName);
            }
            if (isJavaLang(simpleName)) {
                return "java.lang." + simpleName;
            }
            return owner.packageName().isBlank() ? rawName : owner.packageName() + "." + rawName;
        }
    }

    private boolean isNullable(Type type, FieldDeclaration field) {
        if (type.isPrimitiveType()) {
            return false;
        }
        return field.getAnnotations().stream()
                .map(annotation -> annotation.getName().getIdentifier())
                .noneMatch(NON_NULL_ANNOTATIONS::contains);
    }

    private String description(FieldDeclaration field) {
        return field.getJavadocComment()
                .map(comment -> comment.parse().getDescription().toText().trim())
                .filter(text -> !text.isBlank())
                .or(() -> field.getComment().map(Comment::getContent).map(String::trim).filter(text -> !text.isBlank()))
                .orElse(null);
    }

    private boolean isJavaLang(String name) {
        return Set.of("String", "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double",
                "Character", "Object", "Enum").contains(name);
    }

    private String simpleName(String typeName) {
        String withoutGeneric = typeName.contains("<") ? typeName.substring(0, typeName.indexOf('<')) : typeName;
        int lastDot = withoutGeneric.lastIndexOf('.');
        return lastDot < 0 ? withoutGeneric : withoutGeneric.substring(lastDot + 1);
    }

    private record ParsedClass(
            String packageName,
            Map<String, String> imports,
            ClassOrInterfaceDeclaration declaration
    ) {
        private String qualifiedName() {
            return packageName.isBlank() ? declaration.getNameAsString() : packageName + "." + declaration.getNameAsString();
        }
    }

    private record TypeShape(String javaType, String elementTypeName, boolean collection) {
    }
}
