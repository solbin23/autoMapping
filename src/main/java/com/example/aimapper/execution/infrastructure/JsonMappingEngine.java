package com.example.aimapper.execution.infrastructure;

import com.example.aimapper.ai.domain.AiMappingSuggestion.Target;
import com.example.aimapper.execution.application.MappingExecutionException;
import com.example.aimapper.execution.domain.MappingExecutionResult;
import com.example.aimapper.generation.application.NoApprovedMappingsException;
import com.example.aimapper.generation.domain.MappingDecision;
import com.example.aimapper.generation.domain.MappingProject;
import com.example.aimapper.schema.domain.SchemaField;
import com.example.aimapper.schema.domain.SchemaSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 승인된 필드 경로를 사용해 AS-IS JSON을 TO-BE JSON으로 변환한다.
 * Java 코드를 생성하거나 AI를 호출하지 않으며 중첩 객체와 같은 깊이의 컬렉션을 지원한다.
 */
@Component
public class JsonMappingEngine {
    private static final Set<String> NUMERIC_TYPES = Set.of(
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.math.BigDecimal", "java.math.BigInteger");

    private final ObjectMapper objectMapper;

    public JsonMappingEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 선택한 클래스 쌍의 승인 결정만 적용해 새로운 대상 JSON을 만든다. */
    public MappingExecutionResult execute(MappingProject project, String sourceClass, String targetClass,
                                          JsonNode sourceData) {
        if (sourceClass == null || sourceClass.isBlank() || targetClass == null || targetClass.isBlank()) {
            throw new IllegalArgumentException("sourceClass and targetClass are required");
        }
        if (sourceData == null || !sourceData.isObject()) {
            throw new IllegalArgumentException("sourceData must be a JSON object");
        }

        List<MappingDecision> approved = project.decisions().stream()
                .filter(decision -> decision.status() == MappingDecision.Status.APPROVED)
                .filter(decision -> decision.source().className().equals(sourceClass))
                .filter(decision -> decision.target().className().equals(targetClass))
                .toList();
        if (approved.isEmpty()) {
            throw new NoApprovedMappingsException(project.id());
        }

        ObjectNode targetData = objectMapper.createObjectNode();
        Set<String> writtenTargets = new HashSet<>();
        for (MappingDecision decision : approved) {
            if (!writtenTargets.add(decision.target().path())) {
                throw failure(decision, "multiple approved sources write the same target path", null);
            }
            SchemaField sourceField = field(project.recommendations().asIs(), decision.source());
            SchemaField targetField = field(project.recommendations().toBe(), decision.target());
            validatePlan(decision, sourceField, targetField);

            List<Segment> sourcePath = segments(decision.source().path());
            List<Segment> targetPath = segments(decision.target().path());
            ReadResult values = read(sourceData, sourcePath, decision);
            for (CollectionMarker marker : values.markers()) {
                initializeCollection(targetData, targetPath, marker, decision);
            }
            for (LocatedValue located : values.values()) {
                JsonNode converted = convert(located.value(), targetField, decision);
                write(targetData, targetPath, located.indexes(), converted, decision);
            }
        }
        return new MappingExecutionResult(project.id(), project.revision(), sourceClass, targetClass,
                targetData, approved.size());
    }

    private ReadResult read(JsonNode root, List<Segment> path, MappingDecision decision) {
        List<LocatedValue> values = new ArrayList<>();
        List<CollectionMarker> markers = new ArrayList<>();
        read(root, path, 0, new ArrayList<>(), 0, values, markers, decision);
        return new ReadResult(values, markers);
    }

    private void read(JsonNode current, List<Segment> path, int offset, List<Integer> indexes,
                      int collectionOrdinal, List<LocatedValue> values, List<CollectionMarker> markers,
                      MappingDecision decision) {
        if (current == null || current.isNull() || current.isMissingNode()) {
            return;
        }
        if (!current.isObject()) {
            throw failure(decision, "expected an object before '" + path.get(offset).name() + "'", null);
        }

        Segment segment = path.get(offset);
        JsonNode child = current.get(segment.name());
        boolean last = offset == path.size() - 1;
        if (!segment.collection()) {
            if (child == null || child.isMissingNode()) child = NullNode.getInstance();
            if (last) {
                values.add(new LocatedValue(List.copyOf(indexes), child));
            } else if (!child.isNull()) {
                read(child, path, offset + 1, indexes, collectionOrdinal, values, markers, decision);
            }
            return;
        }

        int ordinal = collectionOrdinal + 1;
        if (child == null || child.isNull() || child.isMissingNode()) {
            markers.add(new CollectionMarker(List.copyOf(indexes), ordinal, true));
            return;
        }
        if (!child.isArray()) {
            throw failure(decision, "expected an array at '" + segment.name() + "'", null);
        }
        if (child.isEmpty()) {
            markers.add(new CollectionMarker(List.copyOf(indexes), ordinal, false));
            return;
        }
        for (int index = 0; index < child.size(); index++) {
            indexes.add(index);
            JsonNode element = child.get(index);
            if (last) values.add(new LocatedValue(List.copyOf(indexes), element));
            else read(element, path, offset + 1, indexes, ordinal, values, markers, decision);
            indexes.removeLast();
        }
    }

    private void write(ObjectNode root, List<Segment> path, List<Integer> indexes, JsonNode value,
                       MappingDecision decision) {
        JsonNode current = root;
        int indexOffset = 0;
        for (int offset = 0; offset < path.size(); offset++) {
            Segment segment = path.get(offset);
            boolean last = offset == path.size() - 1;
            if (!(current instanceof ObjectNode object)) {
                throw failure(decision, "target path crosses a non-object value", null);
            }
            if (!segment.collection()) {
                if (last) {
                    object.set(segment.name(), value);
                } else {
                    JsonNode next = object.get(segment.name());
                    if (!(next instanceof ObjectNode)) {
                        next = objectMapper.createObjectNode();
                        object.set(segment.name(), next);
                    }
                    current = next;
                }
                continue;
            }

            if (indexOffset >= indexes.size()) {
                throw failure(decision, "source and target collection coordinates do not match", null);
            }
            ArrayNode array = object.get(segment.name()) instanceof ArrayNode existing
                    ? existing : object.putArray(segment.name());
            int arrayIndex = indexes.get(indexOffset++);
            while (array.size() <= arrayIndex) array.addNull();
            if (last) {
                array.set(arrayIndex, value);
            } else {
                JsonNode next = array.get(arrayIndex);
                if (!(next instanceof ObjectNode)) {
                    next = objectMapper.createObjectNode();
                    array.set(arrayIndex, next);
                }
                current = next;
            }
        }
        if (indexOffset != indexes.size()) {
            throw failure(decision, "source and target collection coordinates do not match", null);
        }
    }

    private void initializeCollection(ObjectNode root, List<Segment> path, CollectionMarker marker,
                                      MappingDecision decision) {
        JsonNode current = root;
        int ordinal = 0;
        int indexOffset = 0;
        for (Segment segment : path) {
            if (!(current instanceof ObjectNode object)) {
                throw failure(decision, "target collection path crosses a non-object value", null);
            }
            if (!segment.collection()) {
                JsonNode next = object.get(segment.name());
                if (!(next instanceof ObjectNode)) {
                    next = objectMapper.createObjectNode();
                    object.set(segment.name(), next);
                }
                current = next;
                continue;
            }

            ordinal++;
            if (ordinal == marker.ordinal()) {
                if (marker.nullValue()) object.set(segment.name(), NullNode.getInstance());
                else object.putArray(segment.name());
                return;
            }
            if (indexOffset >= marker.parentIndexes().size()) {
                throw failure(decision, "collection marker coordinates do not match the target path", null);
            }
            ArrayNode array = object.get(segment.name()) instanceof ArrayNode existing
                    ? existing : object.putArray(segment.name());
            int arrayIndex = marker.parentIndexes().get(indexOffset++);
            while (array.size() <= arrayIndex) array.addNull();
            JsonNode next = array.get(arrayIndex);
            if (!(next instanceof ObjectNode)) {
                next = objectMapper.createObjectNode();
                array.set(arrayIndex, next);
            }
            current = next;
        }
    }

    private JsonNode convert(JsonNode value, SchemaField targetField, MappingDecision decision) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            if (!targetField.nullable()) {
                throw failure(decision, "null cannot be assigned to required target", null);
            }
            return NullNode.getInstance();
        }
        try {
            return switch (decision.conversionType()) {
                case DIRECT, COLLECTION_MAPPING -> direct(value, targetField.javaType(), decision);
                case NUMERIC_CONVERSION -> number(value, targetField.javaType(), false, decision);
                case STRING_PARSE -> numberOrScalar(value, targetField.javaType(), decision);
                case FORMAT -> TextNode.valueOf(value.asText());
                case CUSTOM, UNKNOWN -> throw failure(decision,
                        "conversion type " + decision.conversionType() + " is not executable", null);
            };
        } catch (MappingExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(decision, "value conversion failed", exception);
        }
    }

    private JsonNode direct(JsonNode value, String targetType, MappingDecision decision) {
        String boxed = boxed(targetType);
        boolean compatible = switch (boxed) {
            case "java.lang.String", "java.lang.Character" -> value.isTextual();
            case "java.lang.Boolean" -> value.isBoolean();
            default -> NUMERIC_TYPES.contains(boxed) ? value.isNumber() : true;
        };
        if (!compatible) throw failure(decision, "JSON value does not match target type " + targetType, null);
        if (boxed.equals("java.lang.Character") && value.textValue().length() != 1) {
            throw failure(decision, "exactly one character is required", null);
        }
        return value.deepCopy();
    }

    private JsonNode numberOrScalar(JsonNode value, String targetType, MappingDecision decision) {
        if (!value.isTextual()) throw failure(decision, "STRING_PARSE requires a JSON string", null);
        String text = value.textValue();
        return switch (boxed(targetType)) {
            case "java.lang.Boolean" -> {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    throw failure(decision, "invalid boolean value", null);
                }
                yield BooleanNode.valueOf(Boolean.parseBoolean(text));
            }
            case "java.lang.Character" -> {
                if (text.length() != 1) throw failure(decision, "exactly one character is required", null);
                yield TextNode.valueOf(text);
            }
            default -> number(value, targetType, true, decision);
        };
    }

    private JsonNode number(JsonNode value, String targetType, boolean parseText, MappingDecision decision) {
        if (!parseText && !value.isNumber()) {
            throw failure(decision, "numeric conversion requires a JSON number", null);
        }
        BigDecimal decimal;
        try {
            decimal = parseText ? new BigDecimal(value.textValue()) : value.decimalValue();
        } catch (NumberFormatException exception) {
            throw failure(decision, "invalid numeric value", exception);
        }
        return switch (boxed(targetType)) {
            case "java.lang.Byte" -> IntNode.valueOf(decimal.byteValueExact());
            case "java.lang.Short" -> IntNode.valueOf(decimal.shortValueExact());
            case "java.lang.Integer" -> IntNode.valueOf(decimal.intValueExact());
            case "java.lang.Long" -> LongNode.valueOf(decimal.longValueExact());
            case "java.lang.Float" -> {
                float converted = decimal.floatValue();
                if (!Float.isFinite(converted)) throw failure(decision, "float overflow", null);
                yield FloatNode.valueOf(converted);
            }
            case "java.lang.Double" -> {
                double converted = decimal.doubleValue();
                if (!Double.isFinite(converted)) throw failure(decision, "double overflow", null);
                yield DoubleNode.valueOf(converted);
            }
            case "java.math.BigInteger" -> BigIntegerNode.valueOf(decimal.toBigIntegerExact());
            case "java.math.BigDecimal" -> DecimalNode.valueOf(decimal);
            default -> throw failure(decision, "unsupported numeric target type " + targetType, null);
        };
    }

    private void validatePlan(MappingDecision decision, SchemaField source, SchemaField target) {
        if (decision.conversionType() == null) throw failure(decision, "conversion type is required", null);
        if (collectionDepth(source.path()) != collectionDepth(target.path())) {
            throw failure(decision, "source and target collection depth must match", null);
        }
        String from = boxed(source.javaType());
        String to = boxed(target.javaType());
        boolean supported = switch (decision.conversionType()) {
            case DIRECT -> from.equals(to);
            case NUMERIC_CONVERSION -> NUMERIC_TYPES.contains(from) && NUMERIC_TYPES.contains(to);
            case STRING_PARSE -> from.equals("java.lang.String")
                    && (NUMERIC_TYPES.contains(to) || Set.of("java.lang.Boolean", "java.lang.Character").contains(to));
            case FORMAT -> to.equals("java.lang.String");
            case COLLECTION_MAPPING -> source.collection() && target.collection() && from.equals(to);
            case CUSTOM, UNKNOWN -> false;
        };
        if (!supported) {
            throw failure(decision, "unsupported approved conversion: " + source.javaType() + " -> "
                    + target.javaType() + " (" + decision.conversionType() + ")", null);
        }
    }

    private SchemaField field(SchemaSnapshot snapshot, Target target) {
        return snapshot.classes().stream()
                .filter(schemaClass -> schemaClass.qualifiedName().equals(target.className()))
                .flatMap(schemaClass -> schemaClass.fields().stream())
                .filter(field -> field.path().equals(target.path()))
                .findFirst()
                .orElseThrow(() -> new MappingExecutionException(
                        "Unknown schema field " + target.className() + ":" + target.path()));
    }

    private List<Segment> segments(String path) {
        return Arrays.stream(path.split("\\."))
                .map(value -> new Segment(value.replace("[]", ""), value.endsWith("[]")))
                .toList();
    }

    private int collectionDepth(String path) {
        return (path.length() - path.replace("[]", "").length()) / 2;
    }

    private String boxed(String type) {
        return switch (type) {
            case "byte" -> "java.lang.Byte";
            case "short" -> "java.lang.Short";
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "float" -> "java.lang.Float";
            case "double" -> "java.lang.Double";
            case "boolean" -> "java.lang.Boolean";
            case "char" -> "java.lang.Character";
            default -> type;
        };
    }

    private MappingExecutionException failure(MappingDecision decision, String reason, Throwable cause) {
        String message = "Failed to map " + decision.source().className() + ":" + decision.source().path()
                + " -> " + decision.target().className() + ":" + decision.target().path() + ": " + reason;
        return cause == null ? new MappingExecutionException(message)
                : new MappingExecutionException(message, cause);
    }

    private record Segment(String name, boolean collection) {
    }

    private record LocatedValue(List<Integer> indexes, JsonNode value) {
    }

    private record CollectionMarker(List<Integer> parentIndexes, int ordinal, boolean nullValue) {
    }

    private record ReadResult(List<LocatedValue> values, List<CollectionMarker> markers) {
    }
}
