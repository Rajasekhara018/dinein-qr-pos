package com.heuristiq.identity.testsupport;

import org.mockito.Mockito;
import org.objenesis.ObjenesisStd;
import org.springframework.core.ResolvableType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Test helper that builds deterministic, fully populated sample objects (entities, documents, domain objects)
 * by reflection so mapping code can be verified field by field without writing every setter by hand.
 */
public final class BeanPopulator {

    private static final ObjenesisStd OBJENESIS = new ObjenesisStd();
    private static final int MAX_DEPTH = 4;

    private BeanPopulator() {
    }

    /** Builds a populated instance of the given type. */
    public static <T> T populated(Class<T> type) {
        return type.cast(value(ResolvableType.forClass(type), new Object[0], 0));
    }

    /** Builds a value for a resolved (possibly generic) type; method-level type variables fall back to the first Class argument. */
    public static Object value(ResolvableType type, Object[] args) {
        return value(type, args, 0);
    }

    private static Object value(ResolvableType type, Object[] args, int depth) {
        Class<?> raw = type.resolve();
        if (raw == null) {
            raw = firstClassArg(args);
            if (raw == null) {
                return "sample";
            }
            return value(ResolvableType.forClass(raw), args, depth);
        }
        if (raw == void.class || raw == Void.class) {
            return null;
        }
        Object simple = simple(raw);
        if (simple != null) {
            return simple;
        }
        if (raw == Optional.class) {
            return Optional.ofNullable(value(type.getGeneric(0), args, depth));
        }
        if (raw == Page.class) {
            return new PageImpl<>(new ArrayList<>(List.of(value(type.getGeneric(0), args, depth))), PageRequest.of(0, 5), 1);
        }
        if (raw == Slice.class) {
            return new SliceImpl<>(new ArrayList<>(List.of(value(type.getGeneric(0), args, depth))), PageRequest.of(0, 5), false);
        }
        if (raw == Stream.class) {
            return Stream.of(value(type.getGeneric(0), args, depth));
        }
        if (Set.class.isAssignableFrom(raw) && raw.isAssignableFrom(HashSet.class)) {
            Set<Object> set = new HashSet<>();
            set.add(value(type.getGeneric(0), args, depth + 1));
            return set;
        }
        if ((Collection.class.isAssignableFrom(raw) || raw == Iterable.class) && raw.isAssignableFrom(ArrayList.class)) {
            List<Object> list = new ArrayList<>();
            list.add(value(type.getGeneric(0), args, depth + 1));
            return list;
        }
        if (Map.class.isAssignableFrom(raw) && raw.isAssignableFrom(LinkedHashMap.class)) {
            Map<Object, Object> map = new LinkedHashMap<>();
            map.put(value(type.getGeneric(0), args, depth + 1), value(type.getGeneric(1), args, depth + 1));
            return map;
        }
        if (raw.isArray()) {
            if (raw.getComponentType() == byte.class) {
                return new byte[]{1, 2, 3};
            }
            Object array = java.lang.reflect.Array.newInstance(raw.getComponentType(), 1);
            java.lang.reflect.Array.set(array, 0, value(ResolvableType.forClass(raw.getComponentType()), args, depth + 1));
            return array;
        }
        if (raw == org.springframework.data.domain.Pageable.class) {
            return PageRequest.of(0, 5);
        }
        if (raw == org.springframework.data.domain.Sort.class) {
            return org.springframework.data.domain.Sort.by("id");
        }
        if (raw.isRecord()) {
            return newRecord(raw, args, depth + 1);
        }
        if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
            return Mockito.mock(raw);
        }
        if (depth >= MAX_DEPTH) {
            return null;
        }
        Object bean = OBJENESIS.newInstance(raw);
        populateFields(bean, type, args, depth + 1);
        return bean;
    }

    private static Object newRecord(Class<?> raw, Object[] args, int depth) {
        var components = raw.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            Object v = depth > MAX_DEPTH ? null : value(ResolvableType.forType(components[i].getGenericType()), args, depth);
            values[i] = v == null && types[i].isPrimitive() ? value(ResolvableType.forClass(types[i]), args, depth) : v;
        }
        try {
            var ctor = raw.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build record " + raw, e);
        }
    }

    private static void populateFields(Object bean, ResolvableType type, Object[] args, int depth) {
        for (Class<?> c = bean.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    ResolvableType fieldType = ResolvableType.forField(field, type);
                    Object v = value(fieldType, args, depth);
                    if (v != null) {
                        field.set(bean, v);
                    }
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    // leave the field at its default; the mapping assertion will flag a real mismatch
                }
            }
        }
    }

    private static Class<?> firstClassArg(Object[] args) {
        for (Object a : args) {
            if (a instanceof Class<?> c) {
                return c;
            }
        }
        return null;
    }

    private static Object simple(Class<?> raw) {
        if (raw == String.class || raw == CharSequence.class) {
            return "sample";
        }
        if (raw == boolean.class || raw == Boolean.class) {
            return Boolean.TRUE;
        }
        if (raw == int.class || raw == Integer.class) {
            return 7;
        }
        if (raw == long.class || raw == Long.class) {
            return 7L;
        }
        if (raw == double.class || raw == Double.class) {
            return 7.5d;
        }
        if (raw == float.class || raw == Float.class) {
            return 7.5f;
        }
        if (raw == short.class || raw == Short.class) {
            return (short) 7;
        }
        if (raw == byte.class || raw == Byte.class) {
            return (byte) 7;
        }
        if (raw == char.class || raw == Character.class) {
            return 'x';
        }
        if (raw == BigDecimal.class) {
            return new BigDecimal("12.50");
        }
        if (raw == BigInteger.class) {
            return BigInteger.TEN;
        }
        if (raw == Instant.class) {
            return Instant.parse("2026-01-02T03:04:05Z");
        }
        if (raw == LocalDateTime.class) {
            return LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        }
        if (raw == LocalDate.class) {
            return LocalDate.of(2026, 1, 2);
        }
        if (raw == LocalTime.class) {
            return LocalTime.of(3, 4, 5);
        }
        if (raw == OffsetDateTime.class) {
            return OffsetDateTime.parse("2026-01-02T03:04:05Z");
        }
        if (raw == ZonedDateTime.class) {
            return ZonedDateTime.parse("2026-01-02T03:04:05Z");
        }
        if (raw == ZoneId.class) {
            return ZoneId.of("UTC");
        }
        if (raw == Date.class) {
            return Date.from(Instant.parse("2026-01-02T03:04:05Z"));
        }
        if (raw == UUID.class) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        if (raw == Object.class) {
            return "obj";
        }
        if (raw.isEnum()) {
            Object[] constants = raw.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        return null;
    }
}
