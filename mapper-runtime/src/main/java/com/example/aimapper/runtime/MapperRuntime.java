package com.example.aimapper.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 생성 Mapper가 공통으로 사용하는 객체 경로 접근과 기본 타입 변환 API이다.
 * 생성 코드에 구현을 복사하지 않고 이 라이브러리 버전으로 동작을 통일한다.
 */
public final class MapperRuntime {
    public static final String VERSION = "1.0.0-SNAPSHOT";
    public static final String MAVEN_COORDINATE = "com.example.aimapper:mapper-runtime:" + VERSION;

    private MapperRuntime() {
    }

    /** getter가 없어도 점으로 구분된 중첩 경로에서 값을 읽는다. */
    public static Object readPath(Object bean, String path) {
        return read(bean, path.split("\\."), 0);
    }

    /** setter가 없어도 중첩 경로에 변환된 값을 기록한다. */
    public static void writePath(Object bean, String path, Object value, String conversion) {
        write(bean, path.split("\\."), 0, value, conversion);
    }

    /** 접근 가능한 기본 생성자로 대상 VO 또는 중첩 객체를 생성한다. */
    public static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            if (!constructor.trySetAccessible()) {
                throw new IllegalArgumentException("Constructor is inaccessible: " + type.getName());
            }
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("No usable no-arg constructor: " + type.getName(), exception);
        }
    }

    /** 승인된 변환 방식에 따라 문자열·숫자·불리언·문자 기본 변환을 안전하게 수행한다. */
    public static Object convert(Object value, Class<?> type, String conversion) {
        if (value == null) {
            if (type.isPrimitive()) {
                throw new IllegalArgumentException("Null cannot map to primitive " + type.getName());
            }
            return null;
        }
        Class<?> boxed = box(type);
        if (boxed.isInstance(value)) return value;
        if (conversion.equals("DIRECT")) {
            throw new IllegalArgumentException("DIRECT mapping requires compatible types");
        }
        String text = value.toString();
        if (boxed == String.class) return text;
        if (boxed == Boolean.class) {
            if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Invalid boolean: " + text);
            }
            return Boolean.valueOf(text);
        }
        if (boxed == Character.class) {
            if (text.length() != 1) throw new IllegalArgumentException("Exactly one character required");
            return text.charAt(0);
        }
        BigDecimal number;
        try {
            number = new BigDecimal(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid numeric value", exception);
        }
        if (boxed == Byte.class) return number.byteValueExact();
        if (boxed == Short.class) return number.shortValueExact();
        if (boxed == Integer.class) return number.intValueExact();
        if (boxed == Long.class) return number.longValueExact();
        if (boxed == BigInteger.class) return number.toBigIntegerExact();
        if (boxed == BigDecimal.class) return number;
        if (boxed == Float.class) {
            float result = number.floatValue();
            if (!Float.isFinite(result)) throw new IllegalArgumentException("Float overflow");
            return result;
        }
        if (boxed == Double.class) {
            double result = number.doubleValue();
            if (!Double.isFinite(result)) throw new IllegalArgumentException("Double overflow");
            return result;
        }
        throw new IllegalArgumentException("Unsupported target type: " + type.getName());
    }

    /** 경로를 재귀 탐색하고 [] 구간에서는 모든 컬렉션 원소의 값을 읽는다. */
    private static Object read(Object bean, String[] segments, int index) {
        if (bean == null) return null;
        try {
            String segment = segments[index];
            boolean many = segment.endsWith("[]");
            Object value = field(bean.getClass(), segment.replace("[]", "")).get(bean);
            if (index == segments.length - 1) return many && value != null ? elements(value) : value;
            if (!many || value == null) return read(value, segments, index + 1);
            List<Object> result = new ArrayList<>();
            for (Object element : elements(value)) result.add(read(element, segments, index + 1));
            return result;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Cannot read " + String.join(".", segments), exception);
        }
    }

    /** 필요한 중첩 객체와 컬렉션 원소를 만들면서 대상 경로를 재귀적으로 채운다. */
    private static void write(Object bean, String[] segments, int index, Object value, String conversion) {
        try {
            String segment = segments[index];
            Field field = field(bean.getClass(), segment.replace("[]", ""));
            boolean last = index == segments.length - 1;
            if (segment.endsWith("[]")) {
                if (value == null) {
                    field.set(bean, null);
                    return;
                }
                List<Object> inputs = elements(value);
                Class<?> elementType = elementType(field);
                List<Object> outputs = new ArrayList<>();
                Object previous = field.get(bean);
                List<Object> existing = previous == null ? List.of() : elements(previous);
                for (int itemIndex = 0; itemIndex < inputs.size(); itemIndex++) {
                    Object input = inputs.get(itemIndex);
                    if (last) outputs.add(convert(input, elementType, conversion));
                    else if (input == null) outputs.add(itemIndex < existing.size() ? existing.get(itemIndex) : null);
                    else {
                        Object child = itemIndex < existing.size() && existing.get(itemIndex) != null
                                ? existing.get(itemIndex) : instantiate(elementType);
                        write(child, segments, index + 1, input, conversion);
                        outputs.add(child);
                    }
                }
                if (field.getType().isArray()) {
                    Object array = Array.newInstance(elementType, outputs.size());
                    for (int itemIndex = 0; itemIndex < outputs.size(); itemIndex++) {
                        Array.set(array, itemIndex, outputs.get(itemIndex));
                    }
                    field.set(bean, array);
                } else if (Set.class.isAssignableFrom(field.getType())) {
                    field.set(bean, new LinkedHashSet<>(outputs));
                } else {
                    field.set(bean, outputs);
                }
            } else if (last) {
                field.set(bean, convert(value, field.getType(), conversion));
            } else {
                // 소스 중첩 객체가 없으면 의미 없는 빈 대상 객체 그래프를 만들지 않는다.
                if (value == null) return;
                Object child = field.get(bean);
                if (child == null) {
                    child = instantiate(field.getType());
                    field.set(bean, child);
                }
                write(child, segments, index + 1, value, conversion);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Cannot write " + String.join(".", segments), exception);
        }
    }

    /** 상속 계층까지 검색해 읽고 쓸 수 있는 필드를 찾는다. */
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                if (!field.trySetAccessible()) {
                    throw new IllegalArgumentException("Field is inaccessible: " + name);
                }
                return field;
            } catch (NoSuchFieldException ignored) {
                // 부모 클래스에서 같은 이름의 필드를 계속 찾는다.
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** 배열 또는 구체적인 제네릭 컬렉션에서 원소 타입을 얻는다. */
    private static Class<?> elementType(Field field) {
        if (field.getType().isArray()) return field.getType().getComponentType();
        if (field.getGenericType() instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        throw new IllegalArgumentException("Concrete collection element type required: " + field.getName());
    }

    /** 배열과 Iterable을 동일하게 처리할 수 있도록 원소 목록으로 정규화한다. */
    private static List<Object> elements(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(result::add);
            return result;
        }
        if (value.getClass().isArray()) {
            List<Object> result = new ArrayList<>();
            for (int index = 0; index < Array.getLength(value); index++) result.add(Array.get(value, index));
            return result;
        }
        throw new IllegalArgumentException("Collection value required");
    }

    /** primitive 타입을 대응하는 wrapper 타입으로 정규화한다. */
    private static Class<?> box(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
